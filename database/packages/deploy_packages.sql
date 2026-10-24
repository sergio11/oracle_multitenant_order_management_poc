--=====================================================================
-- deploy_packages.sql
--
-- Deploys the package SPECIFICATIONS and BODIES after the V010 Flyway
-- migration has created the tenant_ord_seq sequence.
--
-- Run as APP_OWNER against PDB_OLTP after `flyway migrate`:
--
--   sqlplus app_owner/<pwd>@//localhost:1521/pdb_oltp @database/packages/deploy_packages.sql
--
-- Specs (pks_*) are included BEFORE bodies (pkb_*): deploying only
-- bodies against stale/missing specs raises PLS-00323 (e.g. missing
-- set_test_stock) or leaves invalid units. V010 and pks_* are kept in
-- sync as duplicate sources of truth (SRS 60).
--=====================================================================

-- Abort on the first SQL error so failures are not masked by later
-- PROMPTs (SQL*Plus default is to continue).
WHENEVER SQLERROR EXIT SQL.SQLCODE
SET DEFINE OFF

-- Application context used by VPD and tenant isolation (SRS 14)
CREATE OR REPLACE CONTEXT app_ctx USING app_owner.pkg_tenant;

--=====================================================================
-- Package specifications (canonical sources in database/packages/)
--=====================================================================
@@pks_pkg_audit.sql
@@pks_pkg_tenant.sql
@@pks_pkg_inventory.sql
@@pks_pkg_order_management.sql

--=====================================================================
-- Package bodies
--=====================================================================
@@pkb_pkg_audit.sql
@@pkb_pkg_tenant.sql
@@pkb_pkg_inventory.sql
@@pkb_pkg_order_management.sql

--=====================================================================
-- Grants for least-privilege users (SRS 58, 59)
-- NOTE (C2): app_reporting is read-only - EXECUTE on the mutating
-- packages (pkg_audit, pkg_inventory, pkg_order_management) is
-- deliberately NOT granted here (revoked on existing DBs by V019).
-- Closure fixes: app_runtime no longer gets pkg_inventory (V023 - the
-- set_test_stock backdoor), app_reporting no longer gets pkg_tenant
-- (V023 - tenant lifecycle must stay out of the read-only user).
GRANT EXECUTE ON pkg_audit            TO app_runtime;
GRANT EXECUTE ON pkg_tenant           TO app_runtime;
GRANT EXECUTE ON pkg_order_management TO app_runtime;

PROMPT Packages deployed successfully.
EXIT SUCCESS
