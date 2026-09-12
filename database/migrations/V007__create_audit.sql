--=====================================================================
-- V007 - AUDIT_EVENT
--
-- Audit domain (SRS 27). Enables reconstruction of relevant operations.
-- PAYLOAD stored as JSON/CLOB to capture request context and changes.
--=====================================================================
CREATE TABLE audit_event (
    audit_id        NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id       NUMBER          NOT NULL,
    entity_type     VARCHAR2(50)    NOT NULL,
    entity_id       VARCHAR2(50)    NOT NULL,
    action          VARCHAR2(50)    NOT NULL,
    actor           VARCHAR2(100)   NOT NULL,
    event_time      TIMESTAMP(6)    DEFAULT SYSTIMESTAMP NOT NULL,
    correlation_id  VARCHAR2(64),
    payload         CLOB
);

COMMENT ON TABLE audit_event IS 'Functional audit events (SRS 27)';
COMMENT ON COLUMN audit_event.tenant_id IS 'Owner tenant (multi-tenancy, may be null for system events)';
COMMENT ON COLUMN audit_event.correlation_id IS 'Correlation ID propagated from HTTP (SRS 56)';
COMMENT ON COLUMN audit_event.payload IS 'JSON payload describing the change';

-- Index for tenant-scoped audit lookup by time (SRS 45)
CREATE INDEX idx_audit_tenant_time ON audit_event (tenant_id, event_time);