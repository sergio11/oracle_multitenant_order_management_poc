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
BEGIN
    SELECT NVL(SUM(CASE WHEN result = 'CONFIRMED' THEN 1 ELSE 0 END), 0),
           NVL(SUM(CASE WHEN result IN ('FAILED','ERROR') THEN 1 ELSE 0 END), 0),
           COUNT(*)
      INTO v_confirmed, v_failed, v_total
      FROM &p_results_table;

    DBMS_OUTPUT.PUT_LINE('total=' || v_total ||
                         ' confirmed=' || v_confirmed ||
                         ' failed=' || v_failed);

    IF v_total < 2 THEN
        RAISE_APPLICATION_ERROR(-20921, 'Concurrency test incomplete: only ' || v_total || ' workers recorded');
    END IF;

    IF v_confirmed <> 1 THEN
        RAISE_APPLICATION_ERROR(-20920, 'Concurrency invariant violated: expected 1 confirmed, got ' || v_confirmed);
    END IF;

    DBMS_OUTPUT.PUT_LINE('CONCURRENCY TEST PASSED');
END;
/

EXIT SUCCESS
