--=====================================================================
-- V027 - Tenant isolation coherence + idempotency retention index
--
-- (1) TIER vs ISOLATION_MODE
--     Routing (TenantRoutingDataSource) decides the target PDB from
--     TIER, while ISOLATION_MODE was an independent, freely-editable
--     column. A row could therefore claim tier=ENTERPRISE with
--     isolation_mode=SHARED_SCHEMA (the seed did exactly that for
--     INITECH), leaving the model with two contradictory answers and
--     no statement of which one wins.
--
--     TIER is the single source of truth: an ENTERPRISE tenant is
--     served from the dedicated PDB. ISOLATION_MODE is derived at
--     write time (pkg_tenant.create_tenant) and constrained here so
--     the contradiction cannot be stored again.
--
-- (2) IDEMPOTENCY retention support: pkg_idempotency.purge_expired
--     scans by (status, created_at); without an index that is a full
--     table scan on an unbounded table.
--=====================================================================

-- Normalize existing contradictory rows before adding the constraint.
UPDATE tenant
   SET isolation_mode = 'DEDICATED_PDB',
       updated_at     = SYSTIMESTAMP
 WHERE tier = 'ENTERPRISE'
   AND isolation_mode <> 'DEDICATED_PDB';

ALTER TABLE tenant ADD CONSTRAINT ck_tenant_enterprise_isolation
    CHECK (tier <> 'ENTERPRISE' OR isolation_mode = 'DEDICATED_PDB');

COMMENT ON COLUMN tenant.tier IS
    'STANDARD | PREMIUM | ENTERPRISE - single source of truth for PDB routing';
COMMENT ON COLUMN tenant.isolation_mode IS
    'Derived from tier at write time: ENTERPRISE => DEDICATED_PDB, otherwise SHARED_SCHEMA';

CREATE INDEX idx_idem_status_created ON idempotency_key (status, created_at);

-- (3) V008 documented the outbox relayer as out of scope. That note is
--     now wrong (pkg_outbox.relay_once implements NEW -> PROCESSING ->
--     PUBLISHED), but V008 is an applied migration and its checksum must
--     not change. Correct the record here instead.
COMMENT ON TABLE outbox_event IS
    'Transactional outbox. The relay half is implemented by pkg_outbox.relay_once (FOR UPDATE SKIP LOCKED); the publish step is a no-op sink (no broker in this POC).';
