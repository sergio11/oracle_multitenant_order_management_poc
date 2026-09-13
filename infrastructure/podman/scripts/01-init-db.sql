--=====================================================================
-- Oracle Local Lab - Initial Database Setup
--
-- This script runs on FIRST container startup against PDB_OLTP.
-- The gvenzl/oracle-free image creates the CDB and PDB automatically
-- from ORACLE_SID / ORACLE_PDB env vars. This script only defines the
-- application schema users:
--
--   APP_OWNER     : owns schema objects (DDL for Flyway migrations)
--   APP_RUNTIME   : application execution (DML/SELECT, least privilege)
--   APP_REPORTING : read-only reporting user
--
-- NOTE: Local lab only. RAC/ASM/Data Guard/GoldenGate remain enterprise
-- target architecture (SRS §86-87, §112).
-- Secrets must come from environment, never hardcoded in Git.
--=====================================================================

--=====================================================================
-- APP_OWNER : owns the application schema objects.
-- Used by Flyway migrations to run DDL.
--=====================================================================
CREATE USER app_owner IDENTIFIED BY "ChangeMe123!";
GRANT CONNECT, RESOURCE TO app_owner;
GRANT CREATE SESSION TO app_owner;
GRANT UNLIMITED TABLESPACE TO app_owner;
GRANT CREATE TABLE, CREATE VIEW, CREATE PROCEDURE, CREATE SEQUENCE,
      CREATE TRIGGER, CREATE MATERIALIZED VIEW, CREATE SYNONYM
  TO app_owner;
-- Privileges required for multi-tenancy isolation (SRS 14, VPD/DBMS_RLS)
GRANT EXECUTE ON SYS.DBMS_RLS TO app_owner;
GRANT CREATE ANY CONTEXT TO app_owner;

--=====================================================================
-- APP_RUNTIME : used by the Spring Boot application at runtime.
-- Least privilege: SELECT on tables/MVs/sequences + EXECUTE on packages.
-- No DDL, no DBA. ORA-00942 surfaces if objects are absent.
--=====================================================================
CREATE USER app_runtime IDENTIFIED BY "ChangeMe123!";
GRANT CREATE SESSION TO app_runtime;
-- Read access on OLTP tables (SRS 58)
GRANT SELECT ON tenant TO app_runtime;
GRANT SELECT ON customer TO app_runtime;
GRANT SELECT ON customer_address TO app_runtime;
GRANT SELECT ON customer_account TO app_runtime;
GRANT SELECT ON product_category TO app_runtime;
GRANT SELECT ON product TO app_runtime;
GRANT SELECT ON warehouse TO app_runtime;
GRANT SELECT ON inventory TO app_runtime;
GRANT SELECT ON sales_order TO app_runtime;
GRANT SELECT ON sales_order_line TO app_runtime;
GRANT SELECT ON order_status_history TO app_runtime;
GRANT SELECT ON payment TO app_runtime;
GRANT SELECT ON outbox_event TO app_runtime;
GRANT SELECT ON idempotency_key TO app_runtime;
GRANT SELECT ON audit_event TO app_runtime;
GRANT SELECT ON mv_order_daily_summary TO app_runtime;
GRANT SELECT ON mv_product_sales_summary TO app_runtime;
-- Execute on PL/SQL packages (SRS 32, business logic close to the data)
GRANT EXECUTE ON pkg_tenant TO app_runtime;
GRANT EXECUTE ON pkg_order_management TO app_runtime;
GRANT EXECUTE ON pkg_inventory TO app_runtime;
GRANT EXECUTE ON pkg_audit TO app_runtime;
GRANT EXECUTE ON pkg_vpd TO app_runtime;
-- Inserts on outbox (Transactional Outbox, SRS 63)
GRANT INSERT, UPDATE ON outbox_event TO app_runtime;

--=====================================================================
-- APP_REPORTING : read-only reporting user (reporting workload).
--=====================================================================
CREATE USER app_reporting IDENTIFIED BY "ChangeMe123!";
GRANT CREATE SESSION TO app_reporting;
GRANT SELECT ON mv_order_daily_summary TO app_reporting;
GRANT SELECT ON mv_product_sales_summary TO app_reporting;
GRANT SELECT ON tenant TO app_reporting;
GRANT SELECT ON customer TO app_reporting;
GRANT SELECT ON product TO app_reporting;
GRANT SELECT ON sales_order TO app_reporting;
GRANT SELECT ON sales_order_line TO app_reporting;
GRANT SELECT ON payment TO app_reporting;
