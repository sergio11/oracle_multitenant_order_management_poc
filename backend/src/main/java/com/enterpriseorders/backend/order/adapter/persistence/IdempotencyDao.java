package com.enterpriseorders.backend.order.adapter.persistence;

import com.enterpriseorders.backend.exception.PlsqlErrorMapper;
import com.enterpriseorders.backend.order.port.output.IdempotencyStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.sql.Timestamp;
import java.util.Optional;

/**
 * JDBC repository for idempotency tracking (SRS §62, FR-014).
 *
 * Uses the idempotency_key table with a unique (tenant_id, idempotency_key)
 * constraint. The first request inserts PENDING; the executor flips the row
 * to COMPLETED together with the serialized response so a retry with the
 * same key returns the original result without re-executing.
 */
@Repository
public class IdempotencyDao implements IdempotencyStore {

    private final JdbcTemplate jdbcTemplate;

    public IdempotencyDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<IdempotencyRecord> findByKey(Long tenantId, String idempotencyKey) {
        return jdbcTemplate.query(
                "SELECT aggregate_id, operation, status, response_payload, request_fingerprint, " +
                "       created_at " +
                "FROM idempotency_key WHERE tenant_id = ? AND idempotency_key = ?",
                rs -> rs.next()
                        ? Optional.of(new IdempotencyRecord(
                                rs.getLong("aggregate_id"),
                                rs.getString("operation"),
                                rs.getString("status"),
                                rs.getString("response_payload"),
                                rs.getString("request_fingerprint"),
                                createdAt(rs.getTimestamp("created_at"))))
                        : Optional.empty(),
                tenantId, idempotencyKey);
    }

    private static java.time.Instant createdAt(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    @Override
    public boolean insertPending(Long tenantId, String idempotencyKey,
                                 String operation, Long aggregateId, String requestFingerprint) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO idempotency_key " +
                    "(tenant_id, idempotency_key, operation, aggregate_id, status, request_fingerprint) " +
                    "VALUES (?, ?, ?, ?, 'PENDING', ?)",
                    tenantId, idempotencyKey, operation, aggregateId, requestFingerprint);
            return true;
        } catch (org.springframework.dao.DuplicateKeyException e) {
            return false;
        }
    }

    @Override
    public int markCompleted(Long tenantId, String idempotencyKey, Long aggregateId, String responsePayload) {
        return jdbcTemplate.update(
                "UPDATE idempotency_key SET status = 'COMPLETED', aggregate_id = ?, response_payload = ? " +
                "WHERE tenant_id = ? AND idempotency_key = ?",
                aggregateId, responsePayload, tenantId, idempotencyKey);
    }

    /**
     * Retention purge through the definer's-rights package: a direct DELETE
     * as APP_RUNTIME would be filtered by VPD (no tenant context in the
     * maintenance thread) and silently delete nothing.
     */
    @Override
    public int purgeExpired(int retentionDays) {
        return jdbcTemplate.execute((Connection connection) -> {
            try (CallableStatement cs = connection.prepareCall(
                    "{ ? = call pkg_idempotency.purge_expired(?) }")) {
                cs.registerOutParameter(1, Types.NUMERIC);
                cs.setInt(2, retentionDays);
                cs.execute();
                return cs.getInt(1);
            } catch (SQLException ex) {
                throw PlsqlErrorMapper.map(ex);
            }
        });
    }
}
