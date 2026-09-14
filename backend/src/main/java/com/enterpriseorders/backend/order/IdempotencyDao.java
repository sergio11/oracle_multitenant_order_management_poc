package com.enterpriseorders.backend.order;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * JDBC repository for idempotency tracking (SRS §62, FR-014, FT-004).
 *
 * Uses the IDEMPOTENCY_KEY table with a unique (tenant_id, idempotency_key)
 * constraint. The first request inserts; any duplicate retry is detected
 * and the original result returned without re-executing.
 */
@Repository
public class IdempotencyDao {

    private final JdbcTemplate jdbcTemplate;

    public IdempotencyDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Looks up an existing idempotency record.
     */
    public Optional<IdempotencyRecord> findByKey(Long tenantId, String idempotencyKey) {
        return jdbcTemplate.query(
                "SELECT aggregate_id, operation, status " +
                "FROM idempotency_key WHERE tenant_id = ? AND idempotency_key = ?",
                rs -> rs.next()
                        ? Optional.of(new IdempotencyRecord(
                                rs.getLong("aggregate_id"),
                                rs.getString("operation"),
                                rs.getString("status")))
                        : Optional.empty(),
                tenantId, idempotencyKey);
    }

    /**
     * Inserts an idempotency record. Returns false if the key already
     * exists (race between two concurrent requests with same key).
     */
    public boolean insertIfAbsent(Long tenantId, String idempotencyKey,
                                  String operation, Long aggregateId) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO idempotency_key " +
                    "(tenant_id, idempotency_key, operation, aggregate_id, status) " +
                    "VALUES (?, ?, ?, ?, 'COMPLETED')",
                    tenantId, idempotencyKey, operation, aggregateId);
            return true;
        } catch (org.springframework.dao.DuplicateKeyException e) {
            return false;
        }
    }

    public boolean insertPending(Long tenantId, String idempotencyKey,
                                 String operation, Long aggregateId) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO idempotency_key " +
                    "(tenant_id, idempotency_key, operation, aggregate_id, status) " +
                    "VALUES (?, ?, ?, ?, 'PENDING')",
                    tenantId, idempotencyKey, operation, aggregateId);
            return true;
        } catch (org.springframework.dao.DuplicateKeyException e) {
            return false;
        }
    }

    public void markCompleted(Long tenantId, String idempotencyKey, String responsePayload) {
        jdbcTemplate.update(
                "UPDATE idempotency_key SET status = 'COMPLETED', response_payload = ? " +
                "WHERE tenant_id = ? AND idempotency_key = ?",
                responsePayload, tenantId, idempotencyKey);
    }

    /**
     * Value object describing a recorded idempotent operation result.
     */
    public record IdempotencyRecord(Long aggregateId, String operation, String status) {
    }
}