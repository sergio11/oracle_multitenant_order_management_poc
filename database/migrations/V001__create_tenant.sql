--=====================================================================
-- V001 - TENANT entity
--
-- Core multi-tenant entity (SRS 12).
-- table owned by APP_OWNER, executed via Flyway as APP_OWNER.
--=====================================================================

CREATE TABLE tenant (
    tenant_id       NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_code     VARCHAR2(30)   NOT NULL,
    name            VARCHAR2(200)  NOT NULL,
    tier            VARCHAR2(20)   NOT NULL,
    status          VARCHAR2(20)   NOT NULL,
    isolation_mode  VARCHAR2(30)   NOT NULL,
    created_at      TIMESTAMP(6)   DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at      TIMESTAMP(6)   DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT ck_tenant_tier CHECK (tier IN ('STANDARD','PREMIUM','ENTERPRISE')),
    CONSTRAINT ck_tenant_status CHECK (status IN ('ACTIVE','SUSPENDED','INACTIVE')),
    CONSTRAINT ck_tenant_isolation CHECK (isolation_mode IN ('SHARED_SCHEMA','DEDICATED_SCHEMA','DEDICATED_PDB')),
    CONSTRAINT uq_tenant_code UNIQUE (tenant_code)
);

-- Optimistic-like guard: updated_at maintained by application/foundation.
COMMENT ON TABLE tenant IS 'Organization tenant (multi-tenancy, SRS 9)';
COMMENT ON COLUMN tenant.tenant_id IS 'Internal identifier';
COMMENT ON COLUMN tenant.tenant_code IS 'Functional unique code';
COMMENT ON COLUMN tenant.tier IS 'STANDARD | PREMIUM | ENTERPRISE';
COMMENT ON COLUMN tenant.status IS 'ACTIVE | SUSPENDED | INACTIVE';
COMMENT ON COLUMN tenant.isolation_mode IS 'SHARED_SCHEMA | DEDICATED_SCHEMA | DEDICATED_PDB';