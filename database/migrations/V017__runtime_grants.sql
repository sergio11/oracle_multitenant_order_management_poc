--=====================================================================
-- V017 - Runtime grants for APP_RUNTIME / APP_REPORTING
--
-- Runs as APP_OWNER AFTER all tables, MVs and packages exist.
--
-- 01-init-db.sql (infrastructure) can no longer grant on schema
-- objects: at first container start Flyway has not run yet, so the
-- tables do not exist. Object grants are therefore applied here,
-- where every object is guaranteed to exist (SRS 58, 59).
--
-- Verified against backend DAO usage:
--   * IdempotencyDao : SELECT/INSERT/UPDATE on idempotency_key
--   * OrderDao       : SELECT on sales_order (+ package calls)
--   * TenantDao      : SELECT on tenant (+ package calls)
--   * ReportingDao   : SELECT on mv_order_daily_summary,
--                      mv_product_sales_summary
-- Business DML (orders, inventory, payments, audit, outbox, history)
-- goes through PL/SQL packages with DEFINER rights (APP_OWNER), so
-- APP_RUNTIME only needs SELECT plus the idempotency_key DML.
-- Parity grants kept for audit/history/outbox INSERT as in the
-- original init script intent.
--
-- Plain GRANTs on purpose: if a user or object is missing, the
-- migration must fail loudly (Flyway marks the version failed).
--=====================================================================

--=====================================================================
-- APP_RUNTIME
--=====================================================================
GRANT SELECT ON tenant                TO app_runtime;
GRANT SELECT ON customer              TO app_runtime;
GRANT SELECT ON customer_address      TO app_runtime;
GRANT SELECT ON customer_account      TO app_runtime;
GRANT SELECT ON product_category      TO app_runtime;
GRANT SELECT ON product               TO app_runtime;
GRANT SELECT ON warehouse             TO app_runtime;
GRANT SELECT ON inventory             TO app_runtime;
GRANT SELECT ON sales_order           TO app_runtime;
GRANT SELECT ON sales_order_line      TO app_runtime;
GRANT SELECT ON order_status_history  TO app_runtime;
GRANT SELECT ON payment               TO app_runtime;
GRANT SELECT ON outbox_event          TO app_runtime;
GRANT SELECT ON audit_event           TO app_runtime;

-- IdempotencyDao performs direct DML on idempotency_key
GRANT SELECT, INSERT, UPDATE ON idempotency_key TO app_runtime;

-- Parity with the original init-script grants (direct inserts only;
-- normal writes happen inside definer-rights packages)
GRANT INSERT ON audit_event          TO app_runtime;
GRANT INSERT ON order_status_history TO app_runtime;
GRANT INSERT, UPDATE ON outbox_event TO app_runtime;

-- Reporting views used by ReportingDao / integration tests
GRANT SELECT ON mv_order_daily_summary   TO app_runtime;
GRANT SELECT ON mv_product_sales_summary TO app_runtime;

-- Business packages (also granted by deploy_packages.sql; repeated
-- here so a migrate-only deployment still has executable packages)
GRANT EXECUTE ON pkg_tenant           TO app_runtime;
GRANT EXECUTE ON pkg_order_management TO app_runtime;
GRANT EXECUTE ON pkg_inventory        TO app_runtime;
GRANT EXECUTE ON pkg_audit            TO app_runtime;
GRANT EXECUTE ON pkg_vpd              TO app_runtime;

--=====================================================================
-- APP_REPORTING (read-only reporting user)
--=====================================================================
GRANT SELECT ON tenant                TO app_reporting;
GRANT SELECT ON customer              TO app_reporting;
GRANT SELECT ON customer_address      TO app_reporting;
GRANT SELECT ON customer_account      TO app_reporting;
GRANT SELECT ON product_category      TO app_reporting;
GRANT SELECT ON product               TO app_reporting;
GRANT SELECT ON warehouse             TO app_reporting;
GRANT SELECT ON inventory             TO app_reporting;
GRANT SELECT ON sales_order           TO app_reporting;
GRANT SELECT ON sales_order_line      TO app_reporting;
GRANT SELECT ON order_status_history  TO app_reporting;
GRANT SELECT ON payment               TO app_reporting;
GRANT SELECT ON outbox_event          TO app_reporting;
GRANT SELECT ON audit_event           TO app_reporting;
GRANT SELECT ON idempotency_key       TO app_reporting;

GRANT SELECT ON mv_order_daily_summary   TO app_reporting;
GRANT SELECT ON mv_product_sales_summary TO app_reporting;

GRANT EXECUTE ON pkg_tenant           TO app_reporting;
GRANT EXECUTE ON pkg_order_management TO app_reporting;
GRANT EXECUTE ON pkg_inventory        TO app_reporting;
GRANT EXECUTE ON pkg_audit            TO app_reporting;
GRANT EXECUTE ON pkg_vpd              TO app_reporting;

-- V017 runtime grants applied successfully.
-- (NOTE: no PROMPT here - PROMPT is a SQL*Plus command, not SQL;
-- Flyway would fail with ORA-00900.)
