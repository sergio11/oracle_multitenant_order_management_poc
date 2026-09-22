--=====================================================================
-- V016 - Fix IDEMPOTENCY_KEY constraints/index
--
-- B4: ck_idem_status only allowed ('COMPLETED','FAILED') but the
-- backend (IdempotencyDao.insertPending) inserts 'PENDING' first and
-- flips it to 'COMPLETED' afterwards -> ORA-02290 on the CHECK
-- constraint. Allow all three lifecycle states.
--
-- NOTE: V012 intentionally creates NO separate index on
-- (tenant_id, idempotency_key) - uq_idem_tenant_key already provides one
-- (a second CREATE INDEX raises ORA-01408).
--=====================================================================

ALTER TABLE idempotency_key DROP CONSTRAINT ck_idem_status;

ALTER TABLE idempotency_key ADD CONSTRAINT ck_idem_status
    CHECK (status IN ('PENDING','COMPLETED','FAILED'));

COMMENT ON COLUMN idempotency_key.status IS 'PENDING | COMPLETED | FAILED';
