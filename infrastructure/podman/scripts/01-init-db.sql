--=====================================================================
-- Oracle Local Lab - Initial Database Setup (first container start)
--
-- Executed by the gvenzl/oracle-free entrypoint as SYS against the FREE
-- CDB instance, AFTER database setup and ORACLE_DATABASE (PDB_OLTP)
-- creation. Scripts under /container-entrypoint-initdb.d are invoked
-- with NO substitution arguments, so passwords are DEFINE'd below.
--
-- Creates the application schema users inside PDB_OLTP:
--
--   APP_OWNER     : owns schema objects (DDL for Flyway migrations)
--   APP_RUNTIME   : application execution (DML/SELECT, least privilege)
--   APP_REPORTING : read-only reporting user
--
-- Object grants (tables, MVs, sequences, packages) are NOT applied
-- here: the schema does not exist yet at first start (Flyway has not
-- run). Those grants are applied later by Flyway migration
-- V017__runtime_grants.sql and/or database/deploy_packages.sql.
--
-- LOCAL LAB ONLY: passwords hardcoded via DEFINE below. Production must
-- inject secrets (e.g. a wrapper .sh exporting env vars into sqlplus
-- substitution, or a secrets manager) - never commit real secrets.
--
-- NOTE: RAC/ASM/Data Guard/GoldenGate remain enterprise target
-- architecture (SRS §86-87, §112).
--=====================================================================

WHENEVER SQLERROR EXIT SQL.SQLCODE

-- Local-lab passwords (see header). Keep in sync with .env.example.
DEFINE app_owner_pwd     = LabOwnerPass1
DEFINE app_runtime_pwd   = LabRuntimePass1
DEFINE app_reporting_pwd = LabReportPass1

-- Init scripts connect to the FREE CDB root; application users must be
-- created inside PDB_OLTP (created because ORACLE_DATABASE=PDB_OLTP).
ALTER SESSION SET CONTAINER = PDB_OLTP;

--=====================================================================
-- APP_OWNER : owns the application schema objects.
-- Used by Flyway migrations to run DDL.
-- Idempotent: skips creation if the user already exists (restarts).
--=====================================================================
DECLARE
  n NUMBER;
BEGIN
  SELECT COUNT(*) INTO n FROM all_users WHERE username = 'APP_OWNER';
  IF n = 0 THEN
    EXECUTE IMMEDIATE 'CREATE USER app_owner IDENTIFIED BY "&app_owner_pwd"';
  END IF;
END;
/
GRANT CONNECT, RESOURCE TO app_owner;
GRANT CREATE SESSION TO app_owner;
GRANT UNLIMITED TABLESPACE TO app_owner;
GRANT CREATE TABLE, CREATE VIEW, CREATE PROCEDURE, CREATE SEQUENCE,
      CREATE TRIGGER, CREATE MATERIALIZED VIEW, CREATE SYNONYM
  TO app_owner;
-- Privileges required for multi-tenancy isolation (SRS 14, VPD/DBMS_RLS)
GRANT EXECUTE ON SYS.DBMS_RLS TO app_owner;
GRANT CREATE ANY CONTEXT TO app_owner;
-- Object grants (tables/MVs/sequences) and package EXECUTE grants are
-- applied AFTER schema creation by Flyway migration
-- V017__runtime_grants.sql (and deploy_packages.sql for pkg_*).

--=====================================================================
-- APP_RUNTIME : used by the Spring Boot application at runtime.
-- Least privilege: session only here; SELECT/DML/EXECUTE grants on
-- APP_OWNER objects come from V017__runtime_grants.sql after Flyway
-- has created the schema (ORA-00942 surfaces if objects are absent).
--=====================================================================
DECLARE
  n NUMBER;
BEGIN
  SELECT COUNT(*) INTO n FROM all_users WHERE username = 'APP_RUNTIME';
  IF n = 0 THEN
    EXECUTE IMMEDIATE 'CREATE USER app_runtime IDENTIFIED BY "&app_runtime_pwd"';
  END IF;
END;
/
GRANT CREATE SESSION TO app_runtime;

--=====================================================================
-- APP_REPORTING : read-only reporting user (reporting workload).
-- SELECT grants on MVs/tables: V017__runtime_grants.sql.
--=====================================================================
DECLARE
  n NUMBER;
BEGIN
  SELECT COUNT(*) INTO n FROM all_users WHERE username = 'APP_REPORTING';
  IF n = 0 THEN
    EXECUTE IMMEDIATE 'CREATE USER app_reporting IDENTIFIED BY "&app_reporting_pwd"';
  END IF;
END;
/
GRANT CREATE SESSION TO app_reporting;
