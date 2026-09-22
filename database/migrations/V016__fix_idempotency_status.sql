--=====================================================================
-- V016 - Fix IDEMPOTENCY_KEY constraints/index
--
-- B4: ck_idem_status only allowed ('COMPLETED','FAILED') but the
-- backend (IdempotencyDao.insertPending) inserts 'PENDING' first and
-- flips it to 'COMPLETED' afterwards -> ORA-02290 on the CHECK
-- constraint. Allow all three lifecycle states.
--
-- Also drops idx_idem_tenant_key: it is redundant with the
-- uq_idem_tenant_key UNIQUE constraint (which provides an index on
-- the same leading columns).
--=====================================================================

ALTER TABLE idempotency_key DROP CONSTRAINT ck_idem_status;

ALTER TABLE idempotency_key ADD CONSTRAINT ck_idem_status
    CHECK (status IN ('PENDING','COMPLETED','FAILED'));

COMMENT ON COLUMN idempotency_key.status IS 'PENDING | COMPLETED | FAILED';

DROP INDEX idx_idem_tenant_key;
