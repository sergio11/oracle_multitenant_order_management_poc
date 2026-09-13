--=====================================================================
-- Integration test: order-to-payment flow (SRS §33, FR-007/008/011/014)
--
-- Verifies end-to-end: tenant -> customer -> order -> line -> confirm
--   -> pay -> report refresh -> MV reflects the confirmed order.
--
-- Usage:
--   sqlplus app_runtime/<pwd>@//localhost:1521/pdb_oltp \
--     @tests/integration/IT_order_to_payment.sql
--
-- Expects SEED data (R__seed_data.sql): tenants OLYMPUS (1), OMEGA (2),
-- with active customers/products/warehouses.
--=====================================================================

SET SERVEROUTPUT ON
WHENEVER SQLERROR EXIT FAILURE

DEFINE p_tenant_id   = 1
DEFINE p_customer_id = 1
DEFINE p_product_id  = 1
DEFINE p_warehouse_id = 1

PROMPT === IT: order-to-payment flow for tenant &&p_tenant_id ===

-- 1) Create order (DRAFT)
VARIABLE v_order_id NUMBER;
EXEC :v_order_id := pkg_order_management.create_order(&p_tenant_id, &p_customer_id, 'USD');
DBMS_OUTPUT.PUT_LINE('Created order_id=' || :v_order_id);

-- 2) Add a line
EXEC pkg_order_management.add_order_line(&p_tenant_id, :v_order_id, &p_product_id, 2, &p_warehouse_id);
DBMS_OUTPUT.PUT_LINE('Added line: qty=2 product_id=' || &p_product_id);

-- 3) Confirm order (reserves inventory + audit + outbox)
EXEC pkg_order_management.confirm_order(&p_tenant_id, :v_order_id, 'TEST_USER', 'IT-' || SYS_GUID());
DBMS_OUTPUT.PUT_LINE('Order confirmed (status should be CONFIRMED)');

-- 4) Register payment
EXEC pkg_order_management.register_payment(&p_tenant_id, :v_order_id, 'PAY-' || :v_order_id, 199.98, 'USD', 'CARD', 'TEST_USER', 'IT-' || SYS_GUID());
DBMS_OUTPUT.PUT_LINE('Payment registered');

-- 5) Refresh MVs
BEGIN
    DBMS_MVIEW.REFRESH('MV_PRODUCT_SALES_SUMMARY', 'C');
    DBMS_MVIEW.REFRESH('MV_ORDER_DAILY_SUMMARY', 'C');
END;
/
DBMS_OUTPUT.PUT_LINE('Materialized views refreshed');

-- 6) Assert MV reflects the confirmed order (revenue > 0)
DECLARE
    v_revenue NUMBER;
    v_orders  NUMBER;
BEGIN
    SELECT NVL(SUM(total_revenue), 0), NVL(SUM(order_count), 0)
    INTO v_revenue, v_orders
    FROM mv_order_daily_summary
    WHERE tenant_id = &p_tenant_id;

    IF v_revenue <= 0 OR v_orders < 1 THEN
        RAISE_APPLICATION_ERROR(-20901, 'MV did not reflect order: revenue=' || v_revenue || ' orders=' || v_orders);
    END IF;
    DBMS_OUTPUT.PUT_LINE('MV assertion OK: revenue=' || v_revenue || ' orders=' || v_orders);
END;
/

-- 7) Assert audit trail recorded the confirm event
DECLARE
    v_events NUMBER;
BEGIN
    SELECT COUNT(*)
    INTO v_events
    FROM audit_event
    WHERE tenant_id = &p_tenant_id
      AND entity_type = 'SALES_ORDER'
      AND entity_id  = :v_order_id
      AND action     = 'STATUS_CONFIRMED';
    IF v_events < 1 THEN
        RAISE_APPLICATION_ERROR(-20902, 'Audit trail missing CONFIRM event for order');
    END IF;
    DBMS_OUTPUT.PUT_LINE('Audit assertion OK: confirm events=' || v_events);
END;
/

-- 8) Assert outbox has the ORDER_CONFIRMED event
DECLARE
    v_outbox NUMBER;
BEGIN
    SELECT COUNT(*)
    INTO v_outbox
    FROM outbox_event
    WHERE tenant_id = &p_tenant_id
      AND event_type = 'ORDER_CONFIRMED';
    IF v_outbox < 1 THEN
        RAISE_APPLICATION_ERROR(-20903, 'Outbox missing ORDER_CONFIRMED event');
    END IF;
    DBMS_OUTPUT.PUT_LINE('Outbox assertion OK: events=' || v_outbox);
END;
/

PROMPT === IT: order-to-payment flow PASSED ===
EXIT SUCCESS