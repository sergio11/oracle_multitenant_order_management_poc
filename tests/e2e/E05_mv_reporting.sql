--=====================================================================
-- E2E-06: Materialized View Reporting Consistency
--
-- Validates that Materialized Views (mv_order_daily_summary,
-- mv_product_sales_summary) reflect committed OLTP data correctly
-- and that tenant isolation is enforced on the MVs too.
--
-- Run as APP_RUNTIME:
--   sqlplus app_runtime/<pwd>@localhost:1521/pdb_oltp @tests/e2e/E06_mv_reporting.sql
--=====================================================================

SET DEFINE OFF;
WHENEVER SQLERROR EXIT FAILURE
SET SERVEROUTPUT ON
ALTER SESSION SET CURRENT_SCHEMA = APP_OWNER;

PROMPT
PROMPT ================================================================
PROMPT  E2E-06: Materialized View Reporting Consistency and VPD Isolation
PROMPT ================================================================

VARIABLE v_tenant_acme   NUMBER
VARIABLE v_tenant_globex NUMBER
VARIABLE v_order_id      NUMBER

-- Setup: create a confirmed order for ACME
DECLARE
  v_cust NUMBER; v_prod NUMBER; v_wh NUMBER; v_price NUMBER;
BEGIN
  SELECT tenant_id INTO :v_tenant_acme   FROM tenant WHERE tenant_code = 'ACME';
  SELECT tenant_id INTO :v_tenant_globex FROM tenant WHERE tenant_code = 'GLOBEX';

  pkg_tenant.set_tenant_context(:v_tenant_acme);
  SELECT MIN(customer_id) INTO v_cust  FROM customer;
  SELECT MIN(product_id)  INTO v_prod  FROM product WHERE status='ACTIVE';
  SELECT MIN(warehouse_id)INTO v_wh    FROM warehouse WHERE status='ACTIVE';
  SELECT unit_price       INTO v_price FROM product WHERE product_id = v_prod;

  :v_order_id := pkg_order_management.create_order(:v_tenant_acme, v_cust, 'EUR');
  pkg_order_management.add_order_line(:v_tenant_acme, :v_order_id, v_prod, 2, v_wh);
  pkg_order_management.confirm_order(:v_tenant_acme, :v_order_id, 'E2E_MV', 'MV-KEY-' || SYS_GUID());
  pkg_order_management.register_payment(:v_tenant_acme, :v_order_id,
    'PAY-MV-' || :v_order_id, 2*v_price, 'EUR', 'CARD', 'E2E_MV', 'MVPAY-' || SYS_GUID());
  DBMS_OUTPUT.PUT_LINE('[SETUP] ACME order created & paid: order_id=' || :v_order_id);
END;
/

-- Refresh MVs
BEGIN
  pkg_tenant.clear_tenant_context;
  refresh_reporting_views;
  DBMS_OUTPUT.PUT_LINE('[STEP-1] Materialized views refreshed via refresh_reporting_views()');
END;
/

-- Assert 1: MV reflects ACME data (with ACME context)
DECLARE
  v_revenue NUMBER;
  v_orders  NUMBER;
BEGIN
  pkg_tenant.set_tenant_context(:v_tenant_acme);
  SELECT NVL(SUM(total_revenue),0), NVL(SUM(order_count),0)
    INTO v_revenue, v_orders
    FROM mv_order_daily_summary WHERE tenant_id = :v_tenant_acme;

  DBMS_OUTPUT.PUT_LINE('[INFO] ACME MV: revenue=' || v_revenue || ' orders=' || v_orders);
  IF v_revenue <= 0 OR v_orders < 1 THEN
    RAISE_APPLICATION_ERROR(-20060, 'FAIL: MV did not reflect ACME order. revenue=' || v_revenue || ' orders=' || v_orders);
  END IF;
  DBMS_OUTPUT.PUT_LINE('[PASS] mv_order_daily_summary reflects ACME confirmed order');
END;
/

-- Assert 2: ACME context cannot see GLOBEX data in MV (VPD on MV)
DECLARE
  v_globex_count NUMBER;
BEGIN
  pkg_tenant.set_tenant_context(:v_tenant_acme);
  SELECT COUNT(*) INTO v_globex_count FROM mv_order_daily_summary
   WHERE tenant_id = :v_tenant_globex;
  IF v_globex_count > 0 THEN
    RAISE_APPLICATION_ERROR(-20061,
      'FAIL: VPD leak on MV! ACME session sees ' || v_globex_count || ' GLOBEX MV rows');
  END IF;
  DBMS_OUTPUT.PUT_LINE('[PASS] VPD on mv_order_daily_summary is tenant-isolated (ACME cannot see GLOBEX rows)');
END;
/

-- Assert 3: mv_product_sales_summary also has data
DECLARE
  v_count NUMBER;
BEGIN
  pkg_tenant.set_tenant_context(:v_tenant_acme);
  SELECT COUNT(*) INTO v_count FROM mv_product_sales_summary WHERE tenant_id = :v_tenant_acme;
  DBMS_OUTPUT.PUT_LINE('[INFO] ACME product sales MV rows: ' || v_count);
  IF v_count < 1 THEN
    RAISE_APPLICATION_ERROR(-20062, 'FAIL: mv_product_sales_summary empty for ACME after refresh');
  END IF;
  DBMS_OUTPUT.PUT_LINE('[PASS] mv_product_sales_summary has ACME data');
END;
/

-- Assert 4: VPD on mv_product_sales_summary (no context = 0 rows)
DECLARE
  v_count NUMBER;
BEGIN
  pkg_tenant.clear_tenant_context;
  SELECT COUNT(*) INTO v_count FROM mv_product_sales_summary;
  IF v_count != 0 THEN
    RAISE_APPLICATION_ERROR(-20063,
      'FAIL: mv_product_sales_summary visible without context: ' || v_count || ' rows');
  END IF;
  DBMS_OUTPUT.PUT_LINE('[PASS] VPD on mv_product_sales_summary is fail-closed (no context = 0 rows)');
END;
/

-- Cleanup
BEGIN
  pkg_tenant.set_tenant_context(:v_tenant_acme);
  EXECUTE IMMEDIATE 'DELETE FROM payment WHERE order_id=:1 AND tenant_id=:2' USING :v_order_id, :v_tenant_acme;
  EXECUTE IMMEDIATE 'DELETE FROM order_status_history WHERE order_id=:1 AND tenant_id=:2' USING :v_order_id, :v_tenant_acme;
  EXECUTE IMMEDIATE 'DELETE FROM sales_order_line WHERE order_id=:1 AND tenant_id=:2' USING :v_order_id, :v_tenant_acme;
  EXECUTE IMMEDIATE 'DELETE FROM outbox_event WHERE aggregate_id=:1 AND tenant_id=:2' USING TO_CHAR(:v_order_id), :v_tenant_acme;
  EXECUTE IMMEDIATE 'DELETE FROM audit_event WHERE entity_id=:1 AND tenant_id=:2' USING TO_CHAR(:v_order_id), :v_tenant_acme;
  EXECUTE IMMEDIATE 'DELETE FROM sales_order WHERE order_id=:1 AND tenant_id=:2' USING :v_order_id, :v_tenant_acme;
  COMMIT;
  pkg_tenant.clear_tenant_context;
  DBMS_OUTPUT.PUT_LINE('[CLEANUP] Test data removed');
EXCEPTION WHEN OTHERS THEN
  ROLLBACK;
  DBMS_OUTPUT.PUT_LINE('[WARN] Cleanup: ' || SQLERRM);
END;
/

PROMPT
PROMPT ================================================================
PROMPT  E2E-06 PASSED: MV Reporting Consistency & VPD Isolation OK
PROMPT ================================================================
EXIT SUCCESS
