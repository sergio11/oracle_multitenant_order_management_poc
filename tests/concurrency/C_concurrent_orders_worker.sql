--=====================================================================
-- Concurrency test worker (SRS §37, §93, FT-003, FT-004)
--
-- Single worker: creates an order, adds one line of 1 unit, attempts
-- to confirm. The first N-1 workers succeed; the last one must fail
-- with INSUFFICIENT_INVENTORY (inventory reserved is serialized by
-- pkg_order_management.confirm_order). Only ONE successful confirm
-- must persist -> proves pessimistic locking works.
--
-- Designed to be launched N times in parallel by:
--   tests/concurrency/run_concurrency_test.bat
--
-- Args (via DEFINE substitution):
--   p_worker_id
--   p_tenant_id
--   p_customer_id
--   p_product_id
--   p_warehouse_id
--   p_results_table
--=====================================================================

SET SERVEROUTPUT ON
WHENEVER SQLERROR EXIT FAILURE

DEFINE p_worker_id
DEFINE p_tenant_id
DEFINE p_customer_id
DEFINE p_product_id
DEFINE p_warehouse_id
DEFINE p_results_table

DECLARE
    v_order_id  NUMBER;
    v_result    VARCHAR2(30);
    v_err_msg   VARCHAR2(4000);
BEGIN
    -- Create + add line + confirm in one go (atomic per worker)
    v_order_id := pkg_order_management.create_order(&p_tenant_id, &p_customer_id, 'USD');
    pkg_order_management.add_order_line(&p_tenant_id, v_order_id, &p_product_id, 1, &p_warehouse_id);

    BEGIN
        pkg_order_management.confirm_order(&p_tenant_id, v_order_id, 'WORKER_' || &p_worker_id, 'CONC-' || SYS_GUID());
        v_result := 'CONFIRMED';
    EXCEPTION
        WHEN OTHERS THEN
            v_result := 'FAILED';
            v_err_msg := SQLERRM;
    END;

    -- Record this worker's outcome
    INSERT INTO ${p_results_table} (worker_id, order_id, result, error_msg, ts)
    VALUES (&p_worker_id, v_order_id, v_result, v_err_msg, SYSTIMESTAMP);
    COMMIT;

    DBMS_OUTPUT.PUT_LINE('Worker &&p_worker_id -> ' || v_result ||
                         ' order_id=' || v_order_id);
EXCEPTION
    WHEN OTHERS THEN
        INSERT INTO ${p_results_table} (worker_id, order_id, result, error_msg, ts)
        VALUES (&p_worker_id, NULL, 'ERROR', SQLERRM, SYSTIMESTAMP);
        COMMIT;
        DBMS_OUTPUT.PUT_LINE('Worker &&p_worker_id -> ERROR: ' || SQLERRM);
        RAISE;
END;
/

EXIT SUCCESS
</arg_value><arg_key>task_progress</arg_key><arg_value>- [x] FASE 1-9 completadas (9 commits)
- [x] FASE 10: IT_order_to_payment.sql + T_security_tenant_isolation.sql + worker concurrency
- [ ] FASE 10: Test de concurrencia (orquestador paralelo + assertions)
- [ ] FASE 10: Commit fase 10
- [ ] FASE 11-12: CI/CD + GoldenGate</arg_value></tool_call>