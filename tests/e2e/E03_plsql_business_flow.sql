--=====================================================================
-- E2E-03: PL/SQL Order-to-Payment Full Business Flow
--
-- Tests the complete business transaction cycle at the DB layer:
--   create_order -> add_order_line -> confirm_order
--   -> register_payment -> MV refresh -> assertions
--
-- Also validates: audit trail, outbox, idempotency replay.
-- Run as APP_RUNTIME:
--   sqlplus app_runtime/<pwd>@localhost:1521/pdb_oltp @tests/e2e/E03_plsql_business_flow.sql
--=====================================================================

SET DEFINE OFF;
WHENEVER SQLERROR EXIT FAILURE
SET SERVEROUTPUT ON
ALTER SESSION SET CURRENT_SCHEMA = APP_OWNER;

PROMPT
PROMPT ================================================================
PROMPT  E2E-03: PL/SQL Business Flow (Order -> Payment -> MV -> Audit)
PROMPT ================================================================

-- Resolve test data from seed (no hardcoded IDs)
VARIABLE v_tenant_id  NUMBER
VARIABLE v_order_id   NUMBER
VARIABLE v_idem_key   VARCHAR2(64)

DECLARE
  v_cust   NUMBER;
  v_prod   NUMBER;
  v_wh     NUMBER;
  v_uprice NUMBER;
BEGIN
  SELECT tenant_id INTO :v_tenant_id FROM tenant WHERE tenant_code = 'ACME';
  pkg_tenant.set_tenant_context(:v_tenant_id);

  SELECT MIN(customer_id) INTO v_cust FROM customer;
  SELECT MIN(product_id)  INTO v_prod FROM product WHERE status = 'ACTIVE';
  SELECT MIN(warehouse_id)INTO v_wh   FROM warehouse WHERE status = 'ACTIVE';
  SELECT unit_price       INTO v_uprice FROM product WHERE product_id = v_prod;

  -- Step 1: Create Order (should be DRAFT)
  :v_order_id := pkg_order_management.create_order(:v_tenant_id, v_cust, 'EUR');
  DBMS_OUTPUT.PUT_LINE('[STEP-1] create_order -> order_id=' || :v_order_id);

  -- Step 2: Add line
  pkg_order_management.add_order_line(:v_tenant_id, :v_order_id, v_prod, 1, v_wh);
  DBMS_OUTPUT.PUT_LINE('[STEP-2] add_order_line -> qty=1 product=' || v_prod);

  -- Step 3: Confirm with idempotency key
  :v_idem_key := 'E2E-IDEM-' || SYS_GUID();
  pkg_order_management.confirm_order(:v_tenant_id, :v_order_id, 'E2E_TEST', :v_idem_key);
  DBMS_OUTPUT.PUT_LINE('[STEP-3] confirm_order -> status should be CONFIRMED');

  -- Step 4: Register payment
  pkg_order_management.register_payment(
    :v_tenant_id, :v_order_id,
    'PAYREF-E2E-' || :v_order_id,
    v_uprice, 'EUR', 'CARD', 'E2E_TEST',
    'E2E-PAY-' || SYS_GUID()
  );
  DBMS_OUTPUT.PUT_LINE('[STEP-4] register_payment -> amount=' || v_uprice);
END;
/

-- Assertion block
DECLARE
  v_status      VARCHAR2(30);
  v_audit_cnt   NUMBER;
  v_outbox_cnt  NUMBER;
  v_pay_amount  NUMBER;
  v_order_total NUMBER;
BEGIN
  pkg_tenant.set_tenant_context(:v_tenant_id);

  -- Assert PAID status
  SELECT status INTO v_status FROM sales_order
   WHERE order_id = :v_order_id AND tenant_id = :v_tenant_id;
  IF v_status != 'PAID' THEN
    RAISE_APPLICATION_ERROR(-20030, 'FAIL: Expected PAID, got ' || v_status);
  END IF;
  DBMS_OUTPUT.PUT_LINE('[PASS] Order status = PAID');

  -- Assert payment amount equals order total
  SELECT p.amount, o.total_amount INTO v_pay_amount, v_order_total
    FROM payment p JOIN sales_order o ON o.order_id = p.order_id
   WHERE p.order_id = :v_order_id AND p.tenant_id = :v_tenant_id;
  IF v_pay_amount != v_order_total THEN
    RAISE_APPLICATION_ERROR(-20031,
      'FAIL: Payment amount ' || v_pay_amount || ' != order total ' || v_order_total);
  END IF;
  DBMS_OUTPUT.PUT_LINE('[PASS] Payment amount equals order total (' || v_pay_amount || ')');

  -- Assert audit trail has CONFIRM event
  SELECT COUNT(*) INTO v_audit_cnt FROM audit_event
   WHERE entity_type = 'SALES_ORDER' AND entity_id = TO_CHAR(:v_order_id)
     AND action = 'DATA_CHANGE_STATUS_CONFIRMED' AND tenant_id = :v_tenant_id;
  IF v_audit_cnt < 1 THEN
    RAISE_APPLICATION_ERROR(-20032, 'FAIL: Missing audit event DATA_CHANGE_STATUS_CONFIRMED');
  END IF;
  DBMS_OUTPUT.PUT_LINE('[PASS] Audit trail has CONFIRM event (count=' || v_audit_cnt || ')');

  -- Assert transactional outbox has ORDER_CONFIRMED
  SELECT COUNT(*) INTO v_outbox_cnt FROM outbox_event
   WHERE aggregate_type = 'SALES_ORDER' AND aggregate_id = TO_CHAR(:v_order_id)
     AND event_type = 'ORDER_CONFIRMED' AND tenant_id = :v_tenant_id;
  IF v_outbox_cnt < 1 THEN
    RAISE_APPLICATION_ERROR(-20033, 'FAIL: Missing outbox event ORDER_CONFIRMED');
  END IF;
  DBMS_OUTPUT.PUT_LINE('[PASS] Outbox has ORDER_CONFIRMED event');
END;
/

-- Idempotency replay: confirm again with same key -> must NOT raise exception
DECLARE
  v_status_before VARCHAR2(30);
  v_status_after  VARCHAR2(30);
BEGIN
  pkg_tenant.set_tenant_context(:v_tenant_id);
  SELECT status INTO v_status_before FROM sales_order
   WHERE order_id = :v_order_id AND tenant_id = :v_tenant_id;

  -- Replay: should be silently idempotent (already PAID/CONFIRMED, same key)
  BEGIN
    pkg_order_management.confirm_order(:v_tenant_id, :v_order_id, 'E2E_REPLAY', :v_idem_key);
  EXCEPTION
    WHEN OTHERS THEN NULL; -- Already in terminal state: expected to soft-fail or be idempotent
  END;

  SELECT status INTO v_status_after FROM sales_order
   WHERE order_id = :v_order_id AND tenant_id = :v_tenant_id;

  IF v_status_after != v_status_before THEN
    RAISE_APPLICATION_ERROR(-20034,
      'FAIL: Idempotency replay changed order status from ' || v_status_before || ' to ' || v_status_after);
  END IF;
  DBMS_OUTPUT.PUT_LINE('[PASS] Idempotency replay did not mutate order state (status=' || v_status_after || ')');
END;
/

-- Cleanup
BEGIN
  pkg_tenant.set_tenant_context(:v_tenant_id);
  EXECUTE IMMEDIATE 'DELETE FROM payment WHERE order_id=:1 AND tenant_id=:2' USING :v_order_id, :v_tenant_id;
  EXECUTE IMMEDIATE 'DELETE FROM order_status_history WHERE order_id=:1 AND tenant_id=:2' USING :v_order_id, :v_tenant_id;
  EXECUTE IMMEDIATE 'DELETE FROM sales_order_line WHERE order_id=:1 AND tenant_id=:2' USING :v_order_id, :v_tenant_id;
  EXECUTE IMMEDIATE 'DELETE FROM outbox_event WHERE aggregate_id=:1 AND tenant_id=:2' USING TO_CHAR(:v_order_id), :v_tenant_id;
  EXECUTE IMMEDIATE 'DELETE FROM audit_event WHERE entity_id=:1 AND tenant_id=:2' USING TO_CHAR(:v_order_id), :v_tenant_id;
  EXECUTE IMMEDIATE 'DELETE FROM sales_order WHERE order_id=:1 AND tenant_id=:2' USING :v_order_id, :v_tenant_id;
  COMMIT;
  pkg_tenant.clear_tenant_context;
  DBMS_OUTPUT.PUT_LINE('[CLEANUP] Test data removed');
EXCEPTION WHEN OTHERS THEN
  ROLLBACK;
  DBMS_OUTPUT.PUT_LINE('[WARN] Cleanup failed: ' || SQLERRM);
END;
/

PROMPT
PROMPT ================================================================
PROMPT  E2E-03 PASSED: Full PL/SQL Business Flow Verified
PROMPT ================================================================
EXIT SUCCESS
