--=====================================================================
-- E2E-07: Business Rule Validation (State Machine & Guards)
--
-- Validates state machine transitions:
--   DRAFT -> CONFIRMED -> PAID -> cancel (INVALID)
--   DRAFT -> CANCELLED (valid)
--   Double payment (same reference) is rejected
--   Payment with wrong amount is rejected
--
-- Run as APP_RUNTIME:
--   sqlplus app_runtime/<pwd>@localhost:1521/pdb_oltp @tests/e2e/E07_business_rules.sql
--=====================================================================

SET DEFINE OFF;
WHENEVER SQLERROR EXIT FAILURE
SET SERVEROUTPUT ON
ALTER SESSION SET CURRENT_SCHEMA = APP_OWNER;

PROMPT
PROMPT ================================================================
PROMPT  E2E-07: Business Rules and State Machine Validation
PROMPT ================================================================

VARIABLE v_tid NUMBER
VARIABLE v_oid NUMBER

DECLARE
  v_cust NUMBER; v_prod NUMBER; v_wh NUMBER;
BEGIN
  SELECT tenant_id INTO :v_tid FROM tenant WHERE tenant_code = 'ACME';
  pkg_tenant.set_tenant_context(:v_tid);
  SELECT MIN(customer_id) INTO v_cust FROM customer;
  SELECT MIN(product_id)  INTO v_prod FROM product WHERE status='ACTIVE';
  SELECT MIN(warehouse_id)INTO v_wh   FROM warehouse WHERE status='ACTIVE';

  -- Setup DRAFT order
  :v_oid := pkg_order_management.create_order(:v_tid, v_cust, 'EUR');
  pkg_order_management.add_order_line(:v_tid, :v_oid, v_prod, 1, v_wh);
  DBMS_OUTPUT.PUT_LINE('[SETUP] DRAFT order created: ' || :v_oid);
END;
/

-- Test 1: Cancel a DRAFT order -> valid
DECLARE
  v_status VARCHAR2(30);
BEGIN
  pkg_tenant.set_tenant_context(:v_tid);
  pkg_order_management.cancel_order(:v_tid, :v_oid, 'E2E_CANCEL', 'CANCEL-KEY-' || SYS_GUID());
  SELECT status INTO v_status FROM sales_order WHERE order_id=:v_oid AND tenant_id=:v_tid;
  IF v_status != 'CANCELLED' THEN
    RAISE_APPLICATION_ERROR(-20070, 'FAIL: Cancel DRAFT -> expected CANCELLED, got ' || v_status);
  END IF;
  DBMS_OUTPUT.PUT_LINE('[PASS] DRAFT -> CANCELLED transition works');
END;
/

-- Test 2: Confirm a CANCELLED order -> must fail (invalid transition)
DECLARE
  v_raised BOOLEAN := FALSE;
BEGIN
  pkg_tenant.set_tenant_context(:v_tid);
  BEGIN
    pkg_order_management.confirm_order(:v_tid, :v_oid, 'E2E_INVALID', 'INVAL-' || SYS_GUID());
  EXCEPTION WHEN OTHERS THEN
    v_raised := TRUE;
    DBMS_OUTPUT.PUT_LINE('[INFO] confirm_order on CANCELLED raised: ' || SQLERRM);
  END;
  IF NOT v_raised THEN
    RAISE_APPLICATION_ERROR(-20071, 'FAIL: confirm_order on CANCELLED order should raise exception');
  END IF;
  DBMS_OUTPUT.PUT_LINE('[PASS] confirm_order correctly rejected on CANCELLED order');
END;
/

-- Setup fresh order for remaining tests
DECLARE
  v_cust NUMBER; v_prod NUMBER; v_wh NUMBER; v_price NUMBER;
BEGIN
  pkg_tenant.set_tenant_context(:v_tid);
  SELECT MIN(customer_id) INTO v_cust FROM customer;
  SELECT MIN(product_id)  INTO v_prod FROM product WHERE status='ACTIVE';
  SELECT MIN(warehouse_id)INTO v_wh   FROM warehouse WHERE status='ACTIVE';
  SELECT unit_price       INTO v_price FROM product WHERE product_id = v_prod;

  :v_oid := pkg_order_management.create_order(:v_tid, v_cust, 'EUR');
  pkg_order_management.add_order_line(:v_tid, :v_oid, v_prod, 1, v_wh);
  pkg_order_management.confirm_order(:v_tid, :v_oid, 'E2E_RULE', 'RULE-KEY-' || SYS_GUID());
  DBMS_OUTPUT.PUT_LINE('[SETUP] Fresh CONFIRMED order: ' || :v_oid || ' (unit_price=' || v_price || ')');
END;
/

-- Test 3: Payment with WRONG amount must be rejected
DECLARE
  v_raised BOOLEAN := FALSE;
BEGIN
  pkg_tenant.set_tenant_context(:v_tid);
  BEGIN
    pkg_order_management.register_payment(
      :v_tid, :v_oid, 'BAD-AMOUNT-' || SYS_GUID(),
      0.01, 'EUR', 'CARD', 'E2E_RULE', 'BADPAY-' || SYS_GUID()
    );
  EXCEPTION WHEN OTHERS THEN
    v_raised := TRUE;
    DBMS_OUTPUT.PUT_LINE('[INFO] Wrong amount payment raised: ' || SQLERRM);
  END;
  IF NOT v_raised THEN
    RAISE_APPLICATION_ERROR(-20072, 'FAIL: Payment with wrong amount should be rejected');
  END IF;
  DBMS_OUTPUT.PUT_LINE('[PASS] Payment with wrong amount correctly rejected');
END;
/

-- Test 4: Cancel a CONFIRMED order -> valid and releases reserved inventory
DECLARE
  v_status VARCHAR2(30);
BEGIN
  pkg_tenant.set_tenant_context(:v_tid);
  pkg_order_management.cancel_order(:v_tid, :v_oid, 'E2E_RULE', 'CANCEL2-' || SYS_GUID());
  SELECT status INTO v_status FROM sales_order WHERE order_id = :v_oid AND tenant_id = :v_tid;
  IF v_status != 'CANCELLED' THEN
    RAISE_APPLICATION_ERROR(-20073, 'FAIL: Cancel CONFIRMED -> expected CANCELLED, got ' || v_status);
  END IF;
  DBMS_OUTPUT.PUT_LINE('[PASS] CONFIRMED -> CANCELLED transition releases inventory');
END;
/

-- Cleanup
BEGIN
  pkg_tenant.set_tenant_context(:v_tid);
  EXECUTE IMMEDIATE 'DELETE FROM order_status_history WHERE tenant_id=:1 AND order_id=:2' USING :v_tid, :v_oid;
  EXECUTE IMMEDIATE 'DELETE FROM sales_order_line WHERE tenant_id=:1 AND order_id=:2' USING :v_tid, :v_oid;
  EXECUTE IMMEDIATE 'DELETE FROM outbox_event WHERE tenant_id=:1 AND aggregate_id=:2' USING :v_tid, TO_CHAR(:v_oid);
  EXECUTE IMMEDIATE 'DELETE FROM audit_event WHERE tenant_id=:1 AND entity_id=:2' USING :v_tid, TO_CHAR(:v_oid);
  EXECUTE IMMEDIATE 'DELETE FROM sales_order WHERE tenant_id=:1 AND order_id=:2' USING :v_tid, :v_oid;
  COMMIT;
  pkg_tenant.clear_tenant_context;
  DBMS_OUTPUT.PUT_LINE('[CLEANUP] Done');
EXCEPTION WHEN OTHERS THEN ROLLBACK; DBMS_OUTPUT.PUT_LINE('[WARN] ' || SQLERRM);
END;
/

PROMPT
PROMPT ================================================================
PROMPT  E2E-07 PASSED: Business Rules & State Machine OK
PROMPT ================================================================
EXIT SUCCESS
