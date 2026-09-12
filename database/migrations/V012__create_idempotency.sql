--=====================================================================
-- V012 - IDEMPOTENCY_KEY
--
-- Idempotency support (SRS §62, FR-014).
--
-- Sensitive operations (confirm order, payment) accept an
-- Idempotency-Key header. The first request records the key together
-- with the resulting order id (or outcome). Any retry with the same
-- key returns the original result WITHOUT re-executing the operation.
--
-- Uniqueness on (TENANT_ID, IDEMPOTENCY_KEY) guarantees no duplicate
-- reservations or double payments under concurrent retries (FT-004).
--=====================================================================
CREATE TABLE idempotency_key (
    idempotency_id   NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id        NUMBER          NOT NULL,
    idempotency_key  VARCHAR2(100)   NOT NULL,
    operation        VARCHAR2(50)    NOT NULL,
    aggregate_id     NUMBER          NOT NULL,
    status           VARCHAR2(20)    NOT NULL,
    response_payload VARCHAR2(2000),
    created_at       TIMESTAMP(6)    DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT fk_idem_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(tenant_id),
    CONSTRAINT ck_idem_status CHECK (status IN ('COMPLETED','FAILED')),
    CONSTRAINT uq_idem_tenant_key UNIQUE (tenant_id, idempotency_key)
);

COMMENT ON TABLE idempotency_key IS 'Idempotency tracking for sensitive operations (SRS 62, FR-014)';
COMMENT ON COLUMN idempotency_key.tenant_id IS 'Owner tenant (multi-tenancy)';
COMMENT ON COLUMN idempotency_key.idempotency_key IS 'Client-provided Idempotency-Key header';
COMMENT ON COLUMN idempotency_key.aggregate_id IS 'Resulting order id (or target entity id)';
COMMENT ON COLUMN idempotency_key.status IS 'COMPLETED | FAILED';

-- Index for fast tenant-scoped lookups by idempotency key
CREATE INDEX idx_idem_tenant_key ON idempotency_key (tenant_id, idempotency_key);
</content>
<task_progress>
- [x] FASE 7: OrderDao + DTOs (OrderLineRequest, CreateOrderRequest, OrderResponse)
- [x] FASE 7: Migración V012 (tabla idempotency_key)
- [ ] FASE 7: IdempotencyDao
- [ ] FASE 7: OrderService (con idempotencia)
- [ ] FASE 7: OrderController (REST endpoints)
- [ ] FASE 7: Commit fase 7
- [ ] FASE 8 a 12 (resto del plan)
</task_progress>