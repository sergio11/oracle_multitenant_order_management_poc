--=====================================================================
-- E2E-01: Multitenant physical isolation
--
-- Proves that both application PDBs are provisioned with the SAME
-- application schema and the SAME tenancy rows:
--
--   1. PDB_OLTP (shared, VPD) and PDB_CORP (dedicated) exist and are open
--   2. APP_OWNER, its core tables, pkg_tenant and the VPD policies exist
--      in BOTH PDBs (same Flyway history)
--   3. Seeded tenants (ACME, GLOBEX, INITECH) carry the SAME tenant_id in
--      both PDBs - the invariant that lets the shared PDB resolve a
--      tenant and the dedicated PDB serve it under that same id
--
-- Ids are captured into SQL*Plus bind variables in PDB_OLTP and compared
-- in PDB_CORP, so a single script covers both containers.
--
-- Run as SYS against the CDB root:
--   rake e2e:physical_isolation
--=====================================================================

SET DEFINE OFF;
WHENEVER SQLERROR EXIT FAILURE
SET SERVEROUTPUT ON

PROMPT
PROMPT ================================================================
PROMPT  E2E-01: Multitenant Physical PDB Isolation
PROMPT ================================================================

-- ------------------------------------------------------------------
-- 1. Topology: both PDBs present and READ WRITE
-- ------------------------------------------------------------------
ALTER SESSION SET CONTAINER = CDB$ROOT;

DECLARE
  v_total NUMBER;
  v_open  NUMBER;
BEGIN
  SELECT COUNT(*) INTO v_total FROM v$pdbs WHERE name IN ('PDB_OLTP', 'PDB_CORP');
  IF v_total != 2 THEN
    RAISE_APPLICATION_ERROR(-20101,
      'FAIL: expected PDB_OLTP and PDB_CORP, found ' || v_total);
  END IF;

  SELECT COUNT(*) INTO v_open FROM v$pdbs
   WHERE name IN ('PDB_OLTP', 'PDB_CORP') AND open_mode = 'READ WRITE';
  IF v_open != 2 THEN
    RAISE_APPLICATION_ERROR(-20102,
      'FAIL: PDB_OLTP/PDB_CORP are not both open READ WRITE');
  END IF;

  DBMS_OUTPUT.PUT_LINE('[PASS] PDB_OLTP and PDB_CORP exist and are READ WRITE');
END;
/

-- ------------------------------------------------------------------
-- 2. Baseline in PDB_OLTP (shared PDB): schema, policies, tenancy ids
-- ------------------------------------------------------------------
ALTER SESSION SET CONTAINER = PDB_OLTP;

VAR v_tenants      NUMBER
VAR v_policies     NUMBER
VAR v_core_tables  NUMBER
VAR v_acme_id      NUMBER
VAR v_globex_id    NUMBER
VAR v_initech_id   NUMBER

DECLARE
  v_users   NUMBER;
  v_pkgs    NUMBER;
  v_ten     NUMBER;
  v_pol     NUMBER;
  v_tables  NUMBER;
  v_acme    NUMBER;
  v_globex  NUMBER;
  v_initech NUMBER;
BEGIN
  SELECT COUNT(*) INTO v_users FROM all_users WHERE username = 'APP_OWNER';
  IF v_users = 0 THEN
    RAISE_APPLICATION_ERROR(-20110, 'FAIL: APP_OWNER missing in PDB_OLTP');
  END IF;

  SELECT COUNT(*) INTO v_tables FROM all_tables
   WHERE owner = 'APP_OWNER'
     AND table_name IN ('TENANT', 'CUSTOMER', 'PRODUCT', 'SALES_ORDER');
  IF v_tables != 4 THEN
    RAISE_APPLICATION_ERROR(-20111,
      'FAIL: core tables missing in PDB_OLTP (' || v_tables || '/4)');
  END IF;

  SELECT COUNT(*) INTO v_pkgs FROM all_objects
   WHERE owner = 'APP_OWNER' AND object_name = 'PKG_TENANT'
     AND object_type = 'PACKAGE BODY';
  IF v_pkgs = 0 THEN
    RAISE_APPLICATION_ERROR(-20112, 'FAIL: pkg_tenant body missing in PDB_OLTP');
  END IF;

  SELECT COUNT(*) INTO v_pol FROM all_policies WHERE owner = 'APP_OWNER';
  IF v_pol = 0 THEN
    RAISE_APPLICATION_ERROR(-20113, 'FAIL: no VPD policies in PDB_OLTP');
  END IF;

  SELECT COUNT(*) INTO v_ten FROM app_owner.tenant WHERE status = 'ACTIVE';
  IF v_ten < 3 THEN
    RAISE_APPLICATION_ERROR(-20114,
      'FAIL: seed not loaded in PDB_OLTP (' || v_ten || ' active tenants)');
  END IF;

  SELECT tenant_id INTO v_acme    FROM app_owner.tenant WHERE tenant_code = 'ACME';
  SELECT tenant_id INTO v_globex  FROM app_owner.tenant WHERE tenant_code = 'GLOBEX';
  SELECT tenant_id INTO v_initech FROM app_owner.tenant WHERE tenant_code = 'INITECH';

  :v_tenants     := v_ten;
  :v_policies    := v_pol;
  :v_core_tables := v_tables;
  :v_acme_id     := v_acme;
  :v_globex_id   := v_globex;
  :v_initech_id  := v_initech;

  DBMS_OUTPUT.PUT_LINE('[PASS] PDB_OLTP: APP_OWNER, ' || v_tables ||
                       ' core tables, pkg_tenant, ' || v_pol || ' VPD policies, ' ||
                       v_ten || ' active tenants');
END;
/

-- ------------------------------------------------------------------
-- 3. PDB_CORP (dedicated PDB): same schema, same tenancy ids
-- ------------------------------------------------------------------
ALTER SESSION SET CONTAINER = PDB_CORP;

DECLARE
  v_users   NUMBER;
  v_pkgs    NUMBER;
  v_ten     NUMBER;
  v_pol     NUMBER;
  v_tables  NUMBER;
  v_acme    NUMBER;
  v_globex  NUMBER;
  v_initech NUMBER;

  PROCEDURE require_equal(p_name IN VARCHAR2, p_expected IN NUMBER, p_actual IN NUMBER) IS
  BEGIN
    IF p_expected != p_actual THEN
      RAISE_APPLICATION_ERROR(-20120,
        'FAIL: ' || p_name || ' differs across PDBs (PDB_OLTP=' ||
        p_expected || ', PDB_CORP=' || p_actual || ')');
    END IF;
  END;
BEGIN
  SELECT COUNT(*) INTO v_users FROM all_users WHERE username = 'APP_OWNER';
  IF v_users = 0 THEN
    RAISE_APPLICATION_ERROR(-20121, 'FAIL: APP_OWNER missing in PDB_CORP');
  END IF;

  SELECT COUNT(*) INTO v_tables FROM all_tables
   WHERE owner = 'APP_OWNER'
     AND table_name IN ('TENANT', 'CUSTOMER', 'PRODUCT', 'SALES_ORDER');
  IF v_tables != 4 THEN
    RAISE_APPLICATION_ERROR(-20122,
      'FAIL: core tables missing in PDB_CORP (' || v_tables || '/4)');
  END IF;

  SELECT COUNT(*) INTO v_pkgs FROM all_objects
   WHERE owner = 'APP_OWNER' AND object_name = 'PKG_TENANT'
     AND object_type = 'PACKAGE BODY';
  IF v_pkgs = 0 THEN
    RAISE_APPLICATION_ERROR(-20123, 'FAIL: pkg_tenant body missing in PDB_CORP');
  END IF;

  SELECT COUNT(*) INTO v_pol FROM all_policies WHERE owner = 'APP_OWNER';
  IF v_pol = 0 THEN
    RAISE_APPLICATION_ERROR(-20124, 'FAIL: no VPD policies in PDB_CORP');
  END IF;

  SELECT COUNT(*) INTO v_ten FROM app_owner.tenant WHERE status = 'ACTIVE';
  IF v_ten < 3 THEN
    RAISE_APPLICATION_ERROR(-20125,
      'FAIL: seed not loaded in PDB_CORP (' || v_ten || ' active tenants)');
  END IF;

  SELECT tenant_id INTO v_acme    FROM app_owner.tenant WHERE tenant_code = 'ACME';
  SELECT tenant_id INTO v_globex  FROM app_owner.tenant WHERE tenant_code = 'GLOBEX';
  SELECT tenant_id INTO v_initech FROM app_owner.tenant WHERE tenant_code = 'INITECH';

  -- Same schema depth in both PDBs.
  require_equal('APP_OWNER core tables', :v_core_tables, v_tables);
  require_equal('VPD policy count',      :v_policies,    v_pol);
  require_equal('active tenant count',   :v_tenants,     v_ten);

  -- The invariant the routing layer depends on: the tenant row that the
  -- shared PDB resolves must have the SAME id in the dedicated PDB.
  require_equal('tenant_id of ACME',    :v_acme_id,    v_acme);
  require_equal('tenant_id of GLOBEX',  :v_globex_id,  v_globex);
  require_equal('tenant_id of INITECH', :v_initech_id, v_initech);

  DBMS_OUTPUT.PUT_LINE('[PASS] PDB_CORP: APP_OWNER, ' || v_tables ||
                       ' core tables, pkg_tenant, ' || v_pol || ' VPD policies, ' ||
                       v_ten || ' active tenants');
  DBMS_OUTPUT.PUT_LINE('[PASS] tenant_id values identical across PDB_OLTP and PDB_CORP');
END;
/

ALTER SESSION SET CONTAINER = CDB$ROOT;

PROMPT
PROMPT ================================================================
PROMPT  E2E-01: Multitenant Physical Isolation PASSED
PROMPT ================================================================
EXIT SUCCESS
