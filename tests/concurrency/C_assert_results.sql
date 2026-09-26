--=====================================================================
-- Concurrency test: assertions
--
-- Asserts that exactly one worker CONFIRMED an order while the others
-- failed (INSUFFICIENT_INVENTORY) - proving pessimistic locking.
-- Run as APP_OWNER (owns the results table) or APP_RUNTIME with grants.
--
-- Args:
--   &1 p_results_table
--=====================================================================

SET SERVEROUTPUT ON
WHENEVER SQLERROR EXIT SQL.SQLCODE

DEFINE p_results_table = &1

SELECT result, COUNT(*) AS cnt
  FROM &p_results_table
 GROUP BY result;

DECLARE
    v_confirmed NUMBER;
    v_failed    NUMBER;
    v_total     NUMBER;
    v_distinct  NUMBER;
BEGIN
    SELECT NVL(SUM(CASE WHEN result = 'CONFIRMED' THEN 1 ELSE 0 END), 0),
           NVL(SUM(CASE WHEN result IN ('FAILED','ERROR') THEN 1 ELSE 0 END), 0),
           COUNT(*),
           COUNT(DISTINCT worker_id)
      INTO v_confirmed, v_failed, v_total, v_distinct
      FROM &p_results_table;

    DBMS_OUTPUT.PUT_LINE('total=' || v_total ||
                         ' confirmed=' || v_confirmed ||
                         ' failed=' || v_failed ||
                         ' distinct_workers=' || v_distinct);

    IF v_total < 2 THEN
        RAISE_APPLICATION_ERROR(-20921, 'Concurrency test incomplete: only ' || v_total || ' workers recorded');
    END IF;

    IF v_confirmed <> 1 THEN
        RAISE_APPLICATION_ERROR(-20920, 'Concurrency invariant violated: expected 1 confirmed, got ' || v_confirmed);
    END IF;

    -- Totals balance: every recorded worker is either confirmed or failed.
    IF v_confirmed + v_failed <> v_total THEN
        RAISE_APPLICATION_ERROR(-20924, 'Result balance violated: confirmed(' || v_confirmed ||
            ')+failed(' || v_failed || ') <> total(' || v_total || ')');
    END IF;

    -- Each worker must have recorded exactly one outcome row.
    IF v_distinct <> v_total THEN
        RAISE_APPLICATION_ERROR(-20925, 'Worker accounting violated: distinct workers(' || v_distinct ||
            ') <> total rows(' || v_total || ')');
    END IF;

    DBMS_OUTPUT.PUT_LINE('CONCURRENCY TEST PASSED');
END;
/

-- Inventory consistency: the single CONFIRMED worker reserved the only
-- unit (setup qty=1 via C_setup_stock, worker qty=1) -> available=0 AND
-- reserved=1 on the test product row. The product/warehouse are resolved
-- from the confirmed order itself (no hardcoded IDs): the reservation
-- warehouse comes from sales_order_line.reserved_warehouse_id (V020),
-- falling back to the first ACTIVE warehouse for legacy rows.
-- VPD-filtered tables need a tenant context even as APP_OWNER
-- (fail-closed 1=0); TENANT itself has no policy, so enumerate tenants
-- and probe for the confirmed order.
DECLARE
    v_order_id NUMBER;
    v_tenant   NUMBER;
    v_product  NUMBER;
    v_wh       NUMBER;
    v_avail    NUMBER;
    v_res      NUMBER;
    v_found    BOOLEAN := FALSE;
BEGIN
    FOR t IN (SELECT tenant_id FROM tenant ORDER BY tenant_id) LOOP
        pkg_tenant.set_tenant_context(t.tenant_id);
        BEGIN
            SELECT r.order_id INTO v_order_id
              FROM &p_results_table r
              JOIN sales_order o ON o.order_id = r.order_id AND o.tenant_id = t.tenant_id
             WHERE r.result = 'CONFIRMED'
               AND ROWNUM = 1;
            v_tenant := t.tenant_id;
            v_found := TRUE;
            EXIT;
        EXCEPTION
            WHEN NO_DATA_FOUND THEN NULL;
        END;
    END LOOP;

    IF NOT v_found THEN
        RAISE_APPLICATION_ERROR(-20923, 'Inventory check: confirmed order not visible under any tenant');
    END IF;

    SELECT l.product_id,
           NVL(l.reserved_warehouse_id,
               (SELECT MIN(warehouse_id) FROM warehouse
                 WHERE tenant_id = v_tenant AND status = 'ACTIVE'))
      INTO v_product, v_wh
      FROM sales_order_line l
     WHERE l.order_id = v_order_id AND l.tenant_id = v_tenant
       AND ROWNUM = 1;

    SELECT available_quantity, reserved_quantity
      INTO v_avail, v_res
      FROM inventory
     WHERE tenant_id = v_tenant AND warehouse_id = v_wh AND product_id = v_product;

    DBMS_OUTPUT.PUT_LINE('inventory check: tenant=' || v_tenant || ' product=' || v_product ||
                         ' warehouse=' || v_wh || ' available=' || v_avail || ' reserved=' || v_res);

    IF v_avail <> 0 OR v_res <> 1 THEN
        RAISE_APPLICATION_ERROR(-20922, 'Inventory inconsistent: expected available=0 reserved=1, got available='
            || v_avail || ' reserved=' || v_res);
    END IF;

    pkg_tenant.clear_tenant_context;
    DBMS_OUTPUT.PUT_LINE('INVENTORY ASSERTION PASSED');
EXCEPTION
    WHEN OTHERS THEN
        BEGIN pkg_tenant.clear_tenant_context; EXCEPTION WHEN OTHERS THEN NULL; END;
        RAISE;
END;
/

EXIT SUCCESS
