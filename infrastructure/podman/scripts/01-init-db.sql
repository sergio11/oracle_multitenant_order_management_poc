-- Usage: @01-init-db.sql <APP_OWNER_PWD> <APP_RUNTIME_PWD> <APP_REPORTING_PWD>
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
CREATE USER app_owner IDENTIFIED BY "&1";
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
CREATE USER app_runtime IDENTIFIED BY "&2";
GRANT CREATE SESSION TO app_runtime;
-- Grants on OLTP tables (SRS 58) - wrapped in PL/SQL for idempotent startup
-- Tables may not exist yet if Flyway has not run; errors are silently ignored.
BEGIN
    EXECUTE IMMEDIATE 'GRANT SELECT ON APP_OWNER.tenant TO app_runtime';
    EXECUTE IMMEDIATE 'GRANT SELECT ON APP_OWNER.customer TO app_runtime';
    EXECUTE IMMEDIATE 'GRANT SELECT ON APP_OWNER.customer_address TO app_runtime';
    EXECUTE IMMEDIATE 'GRANT SELECT ON APP_OWNER.customer_account TO app_runtime';
    EXECUTE IMMEDIATE 'GRANT SELECT ON APP_OWNER.product_category TO app_runtime';
    EXECUTE IMMEDIATE 'GRANT SELECT ON APP_OWNER.product TO app_runtime';
    EXECUTE IMMEDIATE 'GRANT SELECT ON APP_OWNER.warehouse TO app_runtime';
    EXECUTE IMMEDIATE 'GRANT SELECT ON APP_OWNER.inventory TO app_runtime';
    EXECUTE IMMEDIATE 'GRANT SELECT ON APP_OWNER.sales_order TO app_runtime';
    EXECUTE IMMEDIATE 'GRANT SELECT ON APP_OWNER.sales_order_line TO app_runtime';
    EXECUTE IMMEDIATE 'GRANT SELECT ON APP_OWNER.order_status_history TO app_runtime';
    EXECUTE IMMEDIATE 'GRANT SELECT ON APP_OWNER.payment TO app_runtime';
    EXECUTE IMMEDIATE 'GRANT SELECT ON APP_OWNER.outbox_event TO app_runtime';
    EXECUTE IMMEDIATE 'GRANT SELECT ON APP_OWNER.idempotency_key TO app_runtime';
    EXECUTE IMMEDIATE 'GRANT SELECT ON APP_OWNER.audit_event TO app_runtime';
    EXECUTE IMMEDIATE 'GRANT SELECT ON APP_OWNER.mv_order_daily_summary TO app_runtime';
    EXECUTE IMMEDIATE 'GRANT SELECT ON APP_OWNER.mv_product_sales_summary TO app_runtime';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- Execute on PL/SQL packages (SRS 32, business logic close to the data)
GRANT EXECUTE ON pkg_tenant TO app_runtime;
GRANT EXECUTE ON pkg_order_management TO app_runtime;
GRANT EXECUTE ON pkg_inventory TO app_runtime;
GRANT EXECUTE ON pkg_audit TO app_runtime;
GRANT EXECUTE ON pkg_vpd TO app_runtime;

-- Inserts on outbox (Transactional Outbox, SRS 63)
BEGIN
    EXECUTE IMMEDIATE 'GRANT INSERT, UPDATE ON APP_OWNER.outbox_event TO app_runtime';
    EXECUTE IMMEDIATE 'GRANT INSERT ON APP_OWNER.audit_event TO app_runtime';
    EXECUTE IMMEDIATE 'GRANT INSERT ON APP_OWNER.order_status_history TO app_runtime';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

--=====================================================================
-- APP_REPORTING : read-only reporting user (reporting workload).
--=====================================================================
CREATE USER app_reporting IDENTIFIED BY "&3";
GRANT CREATE SESSION TO app_reporting;
BEGIN
    EXECUTE IMMEDIATE 'GRANT SELECT ON APP_OWNER.mv_order_daily_summary TO app_reporting';
    EXECUTE IMMEDIATE 'GRANT SELECT ON APP_OWNER.mv_product_sales_summary TO app_reporting';
    EXECUTE IMMEDIATE 'GRANT SELECT ON APP_OWNER.tenant TO app_reporting';
    EXECUTE IMMEDIATE 'GRANT SELECT ON APP_OWNER.customer TO app_reporting';
    EXECUTE IMMEDIATE 'GRANT SELECT ON APP_OWNER.product TO app_reporting';
    EXECUTE IMMEDIATE 'GRANT SELECT ON APP_OWNER.sales_order TO app_reporting';
    EXECUTE IMMEDIATE 'GRANT SELECT ON APP_OWNER.sales_order_line TO app_reporting';
    EXECUTE IMMEDIATE 'GRANT SELECT ON APP_OWNER.payment TO app_reporting';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
