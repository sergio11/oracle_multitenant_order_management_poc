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
-- Expects SEED data (R__seed_data.sql): tenants ACME (1), GLOBEX (2),
-- INITECH (3) with active customers/products/warehouses.
--=====================================================================

SET SERVEROUTPUT ON
WHENEVER SQLERROR EXIT FAILURE

-- Run as APP_RUNTIME: resolve unqualified names (tables, packages)
-- against the owner schema (no synonyms exist by design).
ALTER SESSION SET CURRENT_SCHEMA = APP_OWNER;

-- Resolve seed IDs dynamically: identity columns are NOT deterministic
-- (e.g. ACME is not necessarily tenant 1), so look up by business code.
-- TENANT has no VPD policy, so it resolves without context; everything
-- else needs the context set first (fail-closed 1=0 otherwise).
COLUMN v_tenant_id NEW_VALUE p_tenant_id
SELECT tenant_id AS v_tenant_id FROM tenant WHERE tenant_code = 'ACME';

PROMPT === IT: order-to-payment flow for tenant &&p_tenant_id ===

-- 0) Set the VPD tenant context for this session (SRS 52/14).
--    Without it every tenant-aware query returns 0 rows (fail-closed).
--    NOTE: direct DBMS_SESSION.SET_CONTEXT is forbidden outside the
--    designated package (ORA-01031) - use the pkg_tenant API.
EXEC pkg_tenant.set_tenant_context(&p_tenant_id);

COLUMN v_customer_id NEW_VALUE p_customer_id
SELECT customer_id AS v_customer_id FROM customer
 WHERE tenant_id = &p_tenant_id AND customer_number = 'C0001';
COLUMN v_product_id NEW_VALUE p_product_id
SELECT product_id AS v_product_id FROM product
 WHERE tenant_id = &p_tenant_id AND sku = 'PRD-001';
COLUMN v_warehouse_id NEW_VALUE p_warehouse_id
SELECT warehouse_id AS v_warehouse_id FROM warehouse
 WHERE tenant_id = &p_tenant_id AND code = 'WH1';
COLUMN v_unit_price NEW_VALUE p_unit_price
SELECT unit_price AS v_unit_price FROM product WHERE product_id = &p_product_id;

-- 1) Create order (DRAFT)
VARIABLE v_order_id NUMBER;
EXEC :v_order_id := pkg_order_management.create_order(&p_tenant_id, &p_customer_id, 'USD');
EXEC DBMS_OUTPUT.PUT_LINE('Created order_id=' || :v_order_id);

-- 2) Add a line (qty=2 of PRD-001 -> total = 2 x unit_price)
EXEC pkg_order_management.add_order_line(&p_tenant_id, :v_order_id, &p_product_id, 2, &p_warehouse_id);
EXEC DBMS_OUTPUT.PUT_LINE('Added line: qty=2 product_id=' || &p_product_id);

-- 3) Confirm order (reserves inventory + audit + outbox)
EXEC pkg_order_management.confirm_order(&p_tenant_id, :v_order_id, 'TEST_USER', 'IT-' || SYS_GUID());
EXEC DBMS_OUTPUT.PUT_LINE('Order confirmed (status should be CONFIRMED)');

-- 3b) Assert order reached CONFIRMED
DECLARE
    v_status VARCHAR2(20);
BEGIN
    SELECT status INTO v_status FROM sales_order
     WHERE tenant_id = &p_tenant_id AND order_id = :v_order_id;
    IF v_status <> 'CONFIRMED' THEN
        RAISE_APPLICATION_ERROR(-20906, 'Expected CONFIRMED after confirm, got ' || v_status);
    END IF;
    DBMS_OUTPUT.PUT_LINE('Status assertion OK: CONFIRMED');
END;
/

-- 4) Register payment (amount must equal the order total)
EXEC pkg_order_management.register_payment(&p_tenant_id, :v_order_id, 'PAY-' || :v_order_id, 2*&p_unit_price, 'USD', 'CARD', 'TEST_USER', 'IT-' || SYS_GUID());
EXEC DBMS_OUTPUT.PUT_LINE('Payment registered');

-- 5) Refresh MVs via the definer's-rights procedure (V018) and assert.
--    Direct DBMS_MVIEW.REFRESH as APP_RUNTIME raises ORA-30372 (VPD on
--    master tables); refresh_reporting_views runs exempt via APP_OWNER.
--    The refresh must run WITHOUT tenant context (an active context
--    breaks it with ORA-01031); clear first in the SAME block, then
--    set the context again for the tenant-scoped asserts below.
BEGIN
    pkg_tenant.clear_tenant_context;
    refresh_reporting_views;
END;
/
EXEC pkg_tenant.set_tenant_context(&p_tenant_id);
DECLARE
    v_revenue   NUMBER;
    v_orders    NUMBER;
BEGIN
    DBMS_OUTPUT.PUT_LINE('Materialized views refreshed');

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

-- 6) Assert payment amount equals the order total + order reached PAID
DECLARE
    v_pay_amount  NUMBER(18,2);
    v_order_total NUMBER(18,2);
    v_status      VARCHAR2(20);
BEGIN
    SELECT p.amount, o.total_amount, o.status
      INTO v_pay_amount, v_order_total, v_status
      FROM payment p
      JOIN sales_order o ON o.order_id = p.order_id AND o.tenant_id = p.tenant_id
     WHERE p.tenant_id = &p_tenant_id
       AND p.order_id = :v_order_id
       AND p.payment_reference = 'PAY-' || :v_order_id;

    IF v_pay_amount <> v_order_total THEN
        RAISE_APPLICATION_ERROR(-20905, 'Payment amount ' || v_pay_amount ||
            ' does not equal order total ' || v_order_total);
    END IF;
    DBMS_OUTPUT.PUT_LINE('Payment amount assertion OK: amount=' || v_pay_amount);

    IF v_status <> 'PAID' THEN
        RAISE_APPLICATION_ERROR(-20907, 'Expected PAID after payment, got ' || v_status);
    END IF;
    DBMS_OUTPUT.PUT_LINE('Status assertion OK: PAID');
END;
/

-- 7) Assert audit trail recorded the confirm event.
--    transition_status writes action 'STATUS_CONFIRMED' via
--    pkg_audit.write_data_change, which prefixes 'DATA_CHANGE_'.
DECLARE
    v_events NUMBER;
BEGIN
    SELECT COUNT(*)
    INTO v_events
    FROM audit_event
    WHERE tenant_id = &p_tenant_id
      AND entity_type = 'SALES_ORDER'
      AND entity_id  = TO_CHAR(:v_order_id)
      AND action     = 'DATA_CHANGE_STATUS_CONFIRMED';
    IF v_events < 1 THEN
        RAISE_APPLICATION_ERROR(-20902, 'Audit trail missing CONFIRM event for order');
    END IF;
    DBMS_OUTPUT.PUT_LINE('Audit assertion OK: confirm events=' || v_events);
END;
/

-- 8) Assert outbox has the ORDER_CONFIRMED event FOR THIS ORDER
DECLARE
    v_outbox NUMBER;
BEGIN
    SELECT COUNT(*)
    INTO v_outbox
    FROM outbox_event
    WHERE tenant_id = &p_tenant_id
      AND aggregate_type = 'SALES_ORDER'
      AND aggregate_id = TO_CHAR(:v_order_id)
      AND event_type = 'ORDER_CONFIRMED';
    IF v_outbox < 1 THEN
        RAISE_APPLICATION_ERROR(-20903, 'Outbox missing ORDER_CONFIRMED event');
    END IF;
    DBMS_OUTPUT.PUT_LINE('Outbox assertion OK: events=' || v_outbox);
END;
/

-- 9) Cleanup: remove this run's rows by business key (the order id just
--    created) so repeated runs do not accumulate. FK-safe order:
--    children (payment/history/lines) before parent; outbox/audit carry
--    no FK but are scoped to this order. Best-effort: APP_RUNTIME holds
--    no DELETE privilege (least-privilege, V017), so a rejection is
--    reported, not failed; a privileged run removes the rows.
BEGIN
    -- Dynamic SQL on purpose: APP_RUNTIME holds no DELETE privilege, so
    -- static DELETEs would fail the whole block at compile time
    -- (ORA-06550+ORA-01031) where no handler can catch them; via
    -- EXECUTE IMMEDIATE a rejection becomes a runtime error handled below.
    EXECUTE IMMEDIATE 'DELETE FROM payment WHERE tenant_id = :1 AND order_id = :2'
        USING &p_tenant_id, :v_order_id;
    EXECUTE IMMEDIATE 'DELETE FROM order_status_history WHERE tenant_id = :1 AND order_id = :2'
        USING &p_tenant_id, :v_order_id;
    EXECUTE IMMEDIATE 'DELETE FROM sales_order_line WHERE tenant_id = :1 AND order_id = :2'
        USING &p_tenant_id, :v_order_id;
    EXECUTE IMMEDIATE 'DELETE FROM outbox_event WHERE tenant_id = :1 AND aggregate_type = ''SALES_ORDER'''
        || ' AND aggregate_id = :2'
        USING &p_tenant_id, TO_CHAR(:v_order_id);
    EXECUTE IMMEDIATE 'DELETE FROM audit_event WHERE tenant_id = :1 AND entity_type = ''SALES_ORDER'''
        || ' AND entity_id = :2'
        USING &p_tenant_id, TO_CHAR(:v_order_id);
    EXECUTE IMMEDIATE 'DELETE FROM sales_order WHERE tenant_id = :1 AND order_id = :2'
        USING &p_tenant_id, :v_order_id;
    COMMIT;
    DBMS_OUTPUT.PUT_LINE('Cleanup OK: test order rows removed');
EXCEPTION
    WHEN OTHERS THEN
        ROLLBACK;
        DBMS_OUTPUT.PUT_LINE('NOTE: cleanup skipped (' || SQLERRM || ')');
END;
/

PROMPT === IT: order-to-payment flow PASSED ===
EXIT SUCCESS
