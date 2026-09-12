--=====================================================================
-- deploy_packages.sql
--
-- Deploys the package BODIES after the V010 Flyway migration has created
-- the package specifications and the tenant_ord_seq sequence.
--
-- Run as APP_OWNER against PDB_OLTP after `flyway migrate`:
--
--   sqlplus app_owner/<pwd>@//localhost:1521/pdb_oltp @database/packages/deploy_packages.sql
--
-- This keeps the canonical source of the bodies in database/packages/
-- while keeping Flyway migrations limited to DDL + specs (SRS 60).
--=====================================================================

-- Application context used by VPD and tenant isolation (SRS 14)
CREATE OR REPLACE CONTEXT app_ctx USING app_owner.pkg_tenant;

--=====================================================================
-- Package bodies
--=====================================================================
@@pkb_pkg_audit.sql
@@pkb_pkg_tenant.sql
@@pkb_pkg_inventory.sql
@@pkb_pkg_order_management.sql

--=====================================================================
-- Grants for least-privilege users (SRS 58, 59)
--=====================================================================
GRANT EXECUTE ON pkg_audit            TO app_runtime;
GRANT EXECUTE ON pkg_audit            TO app_reporting;
GRANT EXECUTE ON pkg_tenant           TO app_runtime;
GRANT EXECUTE ON pkg_tenant           TO app_reporting;
GRANT EXECUTE ON pkg_inventory        TO app_runtime;
GRANT EXECUTE ON pkg_inventory        TO app_reporting;
GRANT EXECUTE ON pkg_order_management TO app_runtime;
GRANT EXECUTE ON pkg_order_management TO app_reporting;

PROMPT Packages deployed successfully.
</content>
<task_progress>
- [x] FASE 1-3 completadas
- [x] FASE 4: PKG_TENANT + PKG_AUDIT + PKG_INVENTORY + PKG_ORDER_MANAGEMENT (spec + body)
- [x] FASE 4: V010 corregida (secuencia + specs, migración Flyway válida)
- [x] FASE 4: deploy_packages.sql (bodies + context + grants)
- [ ] FASE 4: Commit fase 4
- [ ] FASE 5 a 12 (resto del plan)
</task_progress>