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
-- Least privilege: DML/query only against APP_OWNER objects.
-- No DDL, no DBA. ORA-00942 surfaces if objects are absent.
--=====================================================================
CREATE USER app_runtime IDENTIFIED BY "ChangeMe123!";
GRANT CREATE SESSION TO app_runtime;

--=====================================================================
-- APP_REPORTING : read-only reporting user (reporting workload).
--=====================================================================
CREATE USER app_reporting IDENTIFIED BY "ChangeMe123!";
GRANT CREATE SESSION TO app_reporting;

--=====================================================================
-- NOTE: Application context (app_ctx) + VPD policies + package grants
-- are added in later phases (Multi-Tenancy/VPD, Business Logic).
-- This keeps Phase 1 focused on the reproducible container foundation.
--=====================================================================