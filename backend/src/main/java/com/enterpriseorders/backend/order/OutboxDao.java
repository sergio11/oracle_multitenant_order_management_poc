package com.enterpriseorders.backend.order;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Transactional Outbox writer (SRS §63, BR-011).
 *
 * Inserts events into OUTBOX_EVENT within the SAME transaction as the
 * business mutation, guaranteeing atomicity: an order confirmation and
 * its outbox event either both persist or both roll back. A decoupled
 * poller relays events to the message broker; the consumer of events
 * uses the idempotency key to deduplicate (FR-014).
 */
@Repository
public class OutboxDao {

    private final JdbcTemplate jdbcTemplate;

    public OutboxDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void writeEvent(Long tenantId, String aggregateType, String aggregateId,
                           String eventType, String payload) {
        jdbcTemplate.update(
                "INSERT INTO outbox_event (tenant_id, aggregate_type, aggregate_id, event_type, payload, status) " +
                "VALUES (?, ?, ?, ?, ?, 'NEW')",
                tenantId, aggregateType, aggregateId, eventType, payload);
    }
}
