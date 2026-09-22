--=====================================================================
-- Concurrency test worker (SRS §37, §93, FT-003, FT-004)
--
-- Single worker: sets the VPD tenant context, creates an order, adds
-- one line of 1 unit, attempts to confirm. Only ONE successful confirm
-- must persist -> proves pessimistic locking works (others fail with
-- INSUFFICIENT_INVENTORY when stock qty=1).
--
-- Designed to be launched N times in parallel by:
--   tests/concurrency/run_concurrency_test.bat
--   (via tests/concurrency/C_worker_runner.bat)
--
-- Args (via DEFINE substitution, ORDER MATTERS - keep in sync with
-- C_worker_runner.bat / run_concurrency_test.bat):
--   &1 p_worker_id
--   &2 p_tenant_id
--   &3 p_customer_id
--   &4 p_product_id
--   &5 p_warehouse_id
--   &6 p_results_table
--=====================================================================

SET SERVEROUTPUT ON
WHENEVER SQLERROR EXIT FAILURE

DEFINE p_worker_id     = &1
DEFINE p_tenant_id     = &2
DEFINE p_customer_id   = &3
DEFINE p_product_id    = &4
DEFINE p_warehouse_id  = &5
DEFINE p_results_table = &6

-- VPD tenant context is required before any tenant-aware DML (SRS 52)
BEGIN
    DBMS_SESSION.SET_CONTEXT('APP_CTX', 'TENANT_ID', '&p_tenant_id');
END;
/

DECLARE
    v_order_id  NUMBER;
    v_result    VARCHAR2(30);
    v_err_msg   VARCHAR2(4000);
BEGIN
    -- Create + add line + confirm in one transaction (atomic per worker)
    v_order_id := pkg_order_management.create_order(&p_tenant_id, &p_customer_id, 'USD');
    pkg_order_management.add_order_line(&p_tenant_id, v_order_id, &p_product_id, 1, &p_warehouse_id);

    BEGIN
        pkg_order_management.confirm_order(&p_tenant_id, v_order_id, 'WORKER_' || &p_worker_id, 'CONC-' || SYS_GUID());
        v_result := 'CONFIRMED';
        COMMIT;
    EXCEPTION
        WHEN OTHERS THEN
            -- Discard partial work from this worker before recording
            ROLLBACK;
            v_result := 'FAILED';
            v_err_msg := SQLERRM;
    END;

    -- Record this worker's outcome (fresh transaction)
    INSERT INTO &p_results_table (worker_id, order_id, result, error_msg, ts)
    VALUES (&p_worker_id, v_order_id, v_result, v_err_msg, SYSTIMESTAMP);
    COMMIT;

    DBMS_OUTPUT.PUT_LINE('Worker &&p_worker_id -> ' || v_result ||
                         ' order_id=' || v_order_id);
EXCEPTION
    WHEN OTHERS THEN
        ROLLBACK;
        INSERT INTO &p_results_table (worker_id, order_id, result, error_msg, ts)
        VALUES (&p_worker_id, NULL, 'ERROR', SQLERRM, SYSTIMESTAMP);
        COMMIT;
        DBMS_OUTPUT.PUT_LINE('Worker &&p_worker_id -> ERROR: ' || SQLERRM);
        RAISE;
END;
/

EXIT SUCCESS
