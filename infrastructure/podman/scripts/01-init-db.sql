--=====================================================================
-- Oracle Local Lab - Initial Database Setup (first container start)
--
-- Executed by the gvenzl/oracle-free entrypoint as SYS against the FREE
-- CDB instance. Creates:
--   1. CDB-level settings (default file destination)
--   2. Multitenant Physical Isolation:
--      - PDB_OLTP : Shared PDB with VPD for STANDARD/PREMIUM tenants
--      - PDB_CORP : Dedicated PDB for ENTERPRISE physical isolation
--   3. Application schemas and tablespaces inside each PDB
--
-- NOTE: PDB_OLTP and PDB_CORP are provisioned with the SAME application
-- schema (Flyway V001-V0xx + deploy_packages + seed) by `rake qa:db:init`,
-- which runs every step against both services. This script only creates
-- users, tablespaces and the PDBs themselves.
--=====================================================================

WHENEVER SQLERROR EXIT SQL.SQLCODE

-- Local-lab passwords. Keep in sync with .env.example.
DEFINE app_owner_pwd     = LabOwnerPass1
DEFINE app_runtime_pwd   = LabRuntimePass1
DEFINE app_reporting_pwd = LabReportPass1

--=====================================================================
-- 1. CDB ROOT: Default file destination for PDB creation
--=====================================================================
ALTER SESSION SET CONTAINER = CDB$ROOT;

BEGIN
  EXECUTE IMMEDIATE 'ALTER SYSTEM SET db_create_file_dest=''/opt/oracle/oradata'' SCOPE=BOTH';
EXCEPTION
  WHEN OTHERS THEN NULL;
END;
/

--=====================================================================
-- 2. CDB ROOT: Create the dedicated PDB for physical isolation
--=====================================================================
DECLARE
  n NUMBER;
BEGIN
  SELECT COUNT(*) INTO n FROM v$pdbs WHERE name = 'PDB_CORP';
  IF n = 0 THEN
    EXECUTE IMMEDIATE 'CREATE PLUGGABLE DATABASE pdb_corp ADMIN USER corp_admin IDENTIFIED BY "&app_owner_pwd" CREATE_FILE_DEST=''/opt/oracle/oradata''';
    EXECUTE IMMEDIATE 'ALTER PLUGGABLE DATABASE pdb_corp OPEN';
    EXECUTE IMMEDIATE 'ALTER PLUGGABLE DATABASE pdb_corp SAVE STATE';
  END IF;
END;
/

--=====================================================================
-- 3. PDB_OLTP (Shared PDB with VPD + Tablespaces per Tenant)
--=====================================================================
ALTER SESSION SET CONTAINER = PDB_OLTP;

-- Physical Tablespaces for tenant separation within the shared PDB
BEGIN
  EXECUTE IMMEDIATE 'CREATE TABLESPACE ts_tenant_acme DATAFILE SIZE 20M AUTOEXTEND ON NEXT 10M MAXSIZE 200M';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
  EXECUTE IMMEDIATE 'CREATE TABLESPACE ts_tenant_global DATAFILE SIZE 20M AUTOEXTEND ON NEXT 10M MAXSIZE 200M';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- APP_OWNER
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
GRANT EXECUTE ON SYS.DBMS_RLS TO app_owner;
GRANT CREATE ANY CONTEXT TO app_owner;
GRANT EXEMPT ACCESS POLICY TO app_owner;

-- APP_RUNTIME
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

-- APP_REPORTING
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

--=====================================================================
-- 4. PDB_CORP (Dedicated PDB for Enterprise Physical Isolation)
--=====================================================================
ALTER SESSION SET CONTAINER = PDB_CORP;

-- Dedicated Physical Tablespace
BEGIN
  EXECUTE IMMEDIATE 'CREATE TABLESPACE ts_corp_data DATAFILE SIZE 30M AUTOEXTEND ON NEXT 10M MAXSIZE 500M';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- APP_OWNER inside PDB_CORP
DECLARE
  n NUMBER;
BEGIN
  SELECT COUNT(*) INTO n FROM all_users WHERE username = 'APP_OWNER';
  IF n = 0 THEN
    EXECUTE IMMEDIATE 'CREATE USER app_owner IDENTIFIED BY "&app_owner_pwd" DEFAULT TABLESPACE ts_corp_data';
  END IF;
END;
/
GRANT CONNECT, RESOURCE TO app_owner;
GRANT CREATE SESSION TO app_owner;
GRANT UNLIMITED TABLESPACE TO app_owner;
GRANT CREATE TABLE, CREATE VIEW, CREATE PROCEDURE, CREATE SEQUENCE,
      CREATE TRIGGER, CREATE MATERIALIZED VIEW, CREATE SYNONYM
  TO app_owner;
GRANT EXECUTE ON SYS.DBMS_RLS TO app_owner;
GRANT CREATE ANY CONTEXT TO app_owner;
GRANT EXEMPT ACCESS POLICY TO app_owner;

-- APP_RUNTIME inside PDB_CORP
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
-- 5. Return to the default application PDB
--=====================================================================
ALTER SESSION SET CONTAINER = PDB_OLTP;

EXIT SUCCESS;
