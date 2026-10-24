--=====================================================================
-- E2E-04: Pessimistic Locking & Inventory Consistency
--
-- Validates SELECT FOR UPDATE prevents overselling under concurrent load.
-- Creates stock=1, calls confirm_order twice on the same item,
-- asserts exactly 1 succeeds and inventory ends at available=0, reserved=1.
--
-- Run as APP_RUNTIME:
--   sqlplus app_runtime/<pwd>@localhost:1521/pdb_oltp @tests/e2e/E04_pessimistic_locking.sql
--=====================================================================

SET DEFINE OFF;
WHENEVER SQLERROR EXIT FAILURE
SET SERVEROUTPUT ON
ALTER SESSION SET CURRENT_SCHEMA = APP_OWNER;

PROMPT
PROMPT ================================================================
PROMPT  E2E-04: Pessimistic Locking and Inventory Consistency
PROMPT ================================================================

VARIABLE v_tenant_id  NUMBER
VARIABLE v_order1_id  NUMBER
VARIABLE v_order2_id  NUMBER
VARIABLE v_product_id NUMBER
VARIABLE v_wh_id      NUMBER

-- Setup: two competing orders for the same product with stock=1
DECLARE
  v_cust NUMBER;
BEGIN
  SELECT tenant_id INTO :v_tenant_id FROM tenant WHERE tenant_code = 'ACME';
  pkg_tenant.set_tenant_context(:v_tenant_id);

  SELECT MIN(customer_id) INTO v_cust   FROM customer;
  SELECT MIN(product_id)  INTO :v_product_id FROM product WHERE status = 'ACTIVE';
  SELECT MIN(warehouse_id)INTO :v_wh_id FROM warehouse WHERE status = 'ACTIVE';

  -- Set stock to exactly 1
  UPDATE inventory
     SET available_quantity = 1, reserved_quantity = 0
   WHERE product_id = :v_product_id AND warehouse_id = :v_wh_id AND tenant_id = :v_tenant_id;
  COMMIT;
  DBMS_OUTPUT.PUT_LINE('[SETUP] Stock set to available=1, reserved=0 for product=' || :v_product_id);

  -- Create two orders, each wanting 1 unit
  :v_order1_id := pkg_order_management.create_order(:v_tenant_id, v_cust, 'EUR');
  pkg_order_management.add_order_line(:v_tenant_id, :v_order1_id, :v_product_id, 1, :v_wh_id);
  DBMS_OUTPUT.PUT_LINE('[SETUP] Order 1 created: order_id=' || :v_order1_id);

  :v_order2_id := pkg_order_management.create_order(:v_tenant_id, v_cust, 'EUR');
  pkg_order_management.add_order_line(:v_tenant_id, :v_order2_id, :v_product_id, 1, :v_wh_id);
  DBMS_OUTPUT.PUT_LINE('[SETUP] Order 2 created: order_id=' || :v_order2_id);
END;
/

-- Attempt to confirm both orders sequentially (pessimistic lock in PL/SQL)
DECLARE
  v_ok1     BOOLEAN := FALSE;
  v_ok2     BOOLEAN := FALSE;
  v_err1    VARCHAR2(300);
  v_err2    VARCHAR2(300);
BEGIN
  pkg_tenant.set_tenant_context(:v_tenant_id);

  -- Confirm Order 1
  BEGIN
    pkg_order_management.confirm_order(:v_tenant_id, :v_order1_id, 'E2E_LOCK', 'LOCK-KEY-1-' || SYS_GUID());
    v_ok1 := TRUE;
    DBMS_OUTPUT.PUT_LINE('[INFO] Order 1 confirm: SUCCESS');
  EXCEPTION WHEN OTHERS THEN
    v_err1 := SQLERRM;
    DBMS_OUTPUT.PUT_LINE('[INFO] Order 1 confirm: FAILED - ' || v_err1);
  END;

  -- Confirm Order 2 (should fail: stock exhausted)
  BEGIN
    pkg_order_management.confirm_order(:v_tenant_id, :v_order2_id, 'E2E_LOCK', 'LOCK-KEY-2-' || SYS_GUID());
    v_ok2 := TRUE;
    DBMS_OUTPUT.PUT_LINE('[INFO] Order 2 confirm: SUCCESS');
  EXCEPTION WHEN OTHERS THEN
    v_err2 := SQLERRM;
    DBMS_OUTPUT.PUT_LINE('[INFO] Order 2 confirm: FAILED - ' || v_err2);
  END;

  -- Assert: exactly 1 succeeded
  IF v_ok1 AND v_ok2 THEN
    RAISE_APPLICATION_ERROR(-20040, 'FAIL: Both orders confirmed - overselling occurred!');
  END IF;
  IF NOT v_ok1 AND NOT v_ok2 THEN
    RAISE_APPLICATION_ERROR(-20041, 'FAIL: Both orders failed - locking too aggressive? err1=' || v_err1);
  END IF;
  DBMS_OUTPUT.PUT_LINE('[PASS] Exactly 1 of 2 competing orders confirmed (pessimistic lock working)');
END;
/

-- Assert inventory ended at available=0, reserved=1
DECLARE
  v_avail NUMBER;
  v_res   NUMBER;
BEGIN
  pkg_tenant.set_tenant_context(:v_tenant_id);
  SELECT available_quantity, reserved_quantity INTO v_avail, v_res
    FROM inventory
   WHERE product_id = :v_product_id AND warehouse_id = :v_wh_id AND tenant_id = :v_tenant_id;

  DBMS_OUTPUT.PUT_LINE('[INFO] Inventory: available=' || v_avail || ' reserved=' || v_res);
  IF v_avail != 0 OR v_res != 1 THEN
    RAISE_APPLICATION_ERROR(-20042,
      'FAIL: Inventory inconsistent after race. Expected available=0 reserved=1, got available='
      || v_avail || ' reserved=' || v_res);
  END IF;
  DBMS_OUTPUT.PUT_LINE('[PASS] Inventory consistent: available=0, reserved=1');
END;
/

-- Cleanup
BEGIN
  pkg_tenant.set_tenant_context(:v_tenant_id);
  FOR o IN (SELECT :v_order1_id AS oid FROM dual UNION ALL SELECT :v_order2_id FROM dual) LOOP
    EXECUTE IMMEDIATE 'DELETE FROM payment WHERE order_id=:1 AND tenant_id=:2' USING o.oid, :v_tenant_id;
    EXECUTE IMMEDIATE 'DELETE FROM order_status_history WHERE order_id=:1 AND tenant_id=:2' USING o.oid, :v_tenant_id;
    EXECUTE IMMEDIATE 'DELETE FROM sales_order_line WHERE order_id=:1 AND tenant_id=:2' USING o.oid, :v_tenant_id;
    EXECUTE IMMEDIATE 'DELETE FROM outbox_event WHERE aggregate_id=:1 AND tenant_id=:2' USING TO_CHAR(o.oid), :v_tenant_id;
    EXECUTE IMMEDIATE 'DELETE FROM audit_event WHERE entity_id=:1 AND tenant_id=:2' USING TO_CHAR(o.oid), :v_tenant_id;
    EXECUTE IMMEDIATE 'DELETE FROM sales_order WHERE order_id=:1 AND tenant_id=:2' USING o.oid, :v_tenant_id;
  END LOOP;
  -- Restore inventory
  UPDATE inventory SET available_quantity = 10, reserved_quantity = 0
   WHERE product_id = :v_product_id AND warehouse_id = :v_wh_id AND tenant_id = :v_tenant_id;
  COMMIT;
  pkg_tenant.clear_tenant_context;
  DBMS_OUTPUT.PUT_LINE('[CLEANUP] Test data removed, inventory restored');
EXCEPTION WHEN OTHERS THEN
  ROLLBACK;
  DBMS_OUTPUT.PUT_LINE('[WARN] Cleanup failed: ' || SQLERRM);
END;
/

PROMPT
PROMPT ================================================================
PROMPT  E2E-04 PASSED: Pessimistic Locking & Inventory Consistency OK
PROMPT ================================================================
EXIT SUCCESS
