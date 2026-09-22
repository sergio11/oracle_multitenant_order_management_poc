--=====================================================================
-- V008 - OUTBOX_EVENT
--
-- Outbox pattern (SRS 28, 63). Written in the SAME transaction as the
-- business operation to guarantee consistency between DB state and
-- published events. Uniqueness on (AGGREGATE_TYPE, AGGREGATE_ID,
-- EVENT_TYPE, STATUS) gives idempotency for publishers/retries.
--
-- SCOPE NOTE: the outbox publisher/relayer is OUT OF SCOPE for this
-- POC. Events are written atomically with the business transaction;
-- the NEW -> PROCESSING -> PUBLISHED transition will be handled by a
-- future integration component (not implemented here).
--=====================================================================
CREATE TABLE outbox_event (
    event_id        NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id       NUMBER          NOT NULL,
    aggregate_type  VARCHAR2(50)    NOT NULL,
    aggregate_id    VARCHAR2(50)    NOT NULL,
    event_type      VARCHAR2(100)   NOT NULL,
    payload         CLOB,
    status          VARCHAR2(20)    NOT NULL,
    created_at      TIMESTAMP(6)    DEFAULT SYSTIMESTAMP NOT NULL,
    processed_at    TIMESTAMP(6),
    CONSTRAINT ck_outbox_status CHECK (status IN ('NEW','PROCESSING','PUBLISHED','FAILED')),
    CONSTRAINT uq_outbox_aggregate_event UNIQUE (aggregate_type, aggregate_id, event_type, status)
);

COMMENT ON TABLE outbox_event IS 'Outbox pattern events (SRS 28, 63)';
COMMENT ON COLUMN outbox_event.tenant_id IS 'Owner tenant (multi-tenancy)';
COMMENT ON COLUMN outbox_event.status IS 'NEW | PROCESSING | PUBLISHED | FAILED';

-- Index for publisher polling (NEW events per tenant)
CREATE INDEX idx_outbox_tenant_status ON outbox_event (tenant_id, status, created_at);