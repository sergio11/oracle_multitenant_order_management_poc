--=====================================================================
-- E2E-02: VPD Fail-Closed Test
--
-- Proves that VPD is fail-CLOSED: without a tenant context every
-- tenant-protected table returns ZERO rows, and an explicit context
-- correctly restricts to OWN tenant only.
--
-- Run as APP_RUNTIME:
--   sqlplus app_runtime/<pwd>@localhost:1521/pdb_oltp @tests/e2e/E02_vpd_fail_closed.sql
--=====================================================================

SET DEFINE OFF;
WHENEVER SQLERROR EXIT FAILURE
SET SERVEROUTPUT ON

ALTER SESSION SET CURRENT_SCHEMA = APP_OWNER;

PROMPT
PROMPT ================================================================
PROMPT  E2E-02: VPD Fail-Closed and Cross-Tenant Isolation
PROMPT ================================================================

-- 1. Without context -> zero rows (fail-closed)
DECLARE
  v_count NUMBER;
BEGIN
  pkg_tenant.clear_tenant_context;
  SELECT COUNT(*) INTO v_count FROM sales_order;
  IF v_count != 0 THEN
    RAISE_APPLICATION_ERROR(-20010,
      'FAIL: VPD not fail-closed! Without context got ' || v_count || ' rows from sales_order');
  END IF;
  DBMS_OUTPUT.PUT_LINE('[PASS] VPD fail-closed: no context returns 0 rows from sales_order');
END;
/

DECLARE
  v_count NUMBER;
BEGIN
  SELECT COUNT(*) INTO v_count FROM customer;
  IF v_count != 0 THEN
    RAISE_APPLICATION_ERROR(-20011,
      'FAIL: VPD not fail-closed! Without context got ' || v_count || ' rows from customer');
  END IF;
  DBMS_OUTPUT.PUT_LINE('[PASS] VPD fail-closed: no context returns 0 rows from customer');
END;
/

-- 2. With ACME context -> sees only ACME rows, not GLOBEX rows
DECLARE
  v_acme   NUMBER;
  v_globex NUMBER;
  v_acme_orders NUMBER;
  v_globex_orders NUMBER;
BEGIN
  SELECT tenant_id INTO v_acme   FROM tenant WHERE tenant_code = 'ACME';
  SELECT tenant_id INTO v_globex FROM tenant WHERE tenant_code = 'GLOBEX';

  -- Activate ACME context
  pkg_tenant.set_tenant_context(v_acme);

  SELECT COUNT(*) INTO v_acme_orders   FROM sales_order WHERE tenant_id = v_acme;
  SELECT COUNT(*) INTO v_globex_orders FROM sales_order WHERE tenant_id = v_globex;

  DBMS_OUTPUT.PUT_LINE('[INFO] With ACME context: ACME_orders=' || v_acme_orders
    || ' GLOBEX_orders_visible=' || v_globex_orders);

  IF v_globex_orders != 0 THEN
    RAISE_APPLICATION_ERROR(-20012,
      'FAIL: Cross-tenant leak! ACME session can see ' || v_globex_orders || ' GLOBEX orders');
  END IF;
  DBMS_OUTPUT.PUT_LINE('[PASS] ACME context cannot see GLOBEX orders (VPD cross-tenant isolation)');
  pkg_tenant.clear_tenant_context;
END;
/

-- 3. VPD policies count: exactly 16 expected
DECLARE
  v_count NUMBER;
BEGIN
  SELECT COUNT(*) INTO v_count FROM all_policies WHERE object_owner = 'APP_OWNER';
  DBMS_OUTPUT.PUT_LINE('[INFO] VPD policies on APP_OWNER schema: ' || v_count);
  IF v_count < 16 THEN
    RAISE_APPLICATION_ERROR(-20013,
      'FAIL: Expected >= 16 VPD policies, found ' || v_count);
  END IF;
  DBMS_OUTPUT.PUT_LINE('[PASS] VPD coverage: ' || v_count || ' policies found (>= 16 expected)');
END;
/

-- 4. Idempotency key table also protected
DECLARE
  v_count NUMBER;
BEGIN
  SELECT COUNT(*) INTO v_count FROM idempotency_key;
  IF v_count != 0 THEN
    RAISE_APPLICATION_ERROR(-20014,
      'FAIL: idempotency_key visible without context: ' || v_count || ' rows');
  END IF;
  DBMS_OUTPUT.PUT_LINE('[PASS] VPD on idempotency_key is fail-closed');
END;
/

-- 5. Audit events also protected
DECLARE
  v_count NUMBER;
BEGIN
  SELECT COUNT(*) INTO v_count FROM audit_event;
  IF v_count != 0 THEN
    RAISE_APPLICATION_ERROR(-20015,
      'FAIL: audit_event visible without context: ' || v_count || ' rows');
  END IF;
  DBMS_OUTPUT.PUT_LINE('[PASS] VPD on audit_event is fail-closed');
END;
/

PROMPT
PROMPT ================================================================
PROMPT  E2E-02 PASSED: VPD Fail-Closed & Isolation Verified
PROMPT ================================================================
EXIT SUCCESS
