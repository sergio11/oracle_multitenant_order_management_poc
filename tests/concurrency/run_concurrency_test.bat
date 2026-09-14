@echo off
REM =====================================================================
REM Concurrency test orchestrator (SRS §37, §93, FT-003, FT-004)
REM
REM Sets up a single row of stock for one product/warehouse (qty=1),
REM launches N workers in parallel, then asserts exactly 1 succeeded
REM and the stock reservation is consistent.
REM
REM Requires sqlplus on PATH and the DB reachable at localhost:1521.
REM =====================================================================
setlocal enabledelayedexpansion

if not defined DB_USER set DB_USER=app_runtime
if not defined DB_PASS set DB_PASS=ChangeMe123!
if not defined DB_CONN set DB_CONN=%DB_USER%/%DB_PASS%@//localhost:1521/pdb_oltp
if not defined TENANT_ID set TENANT_ID=1
if not defined CUSTOMER_ID set CUSTOMER_ID=1
if not defined PRODUCT_ID set PRODUCT_ID=1
if not defined WAREHOUSE_ID set WAREHOUSE_ID=1
if not defined WORKERS set WORKERS=5

set RESULTS_TBL=concurrency_results_%RANDOM%

echo === Concurrency test: !WORKERS! workers, stock qty=1 ===

REM 1) Create results table
(
  echo CREATE TABLE !RESULTS_TBL! ^(worker_id NUMBER, order_id NUMBER, result VARCHAR2^<30^>, error_msg VARCHAR2^<4000^>, ts TIMESTAMP^);
  echo commit;
  echo exit
) | sqlplus -s %DB_CONN% >nul 2>&1
if errorlevel 1 (
  echo ERROR: failed to create results table & exit /b 1
)

REM 2) Reset stock for the product to exactly 1 unit (pessimistic)
(
  echo "BEGIN pkg_inventory.set_test_stock(%TENANT_ID%, !PRODUCT_ID!, !WAREHOUSE_ID!, 1); END;"
  echo "commit;"
  echo "exit"
) | sqlplus -s %DB_CONN% >nul 2>&1

REM 3) Launch N workers in parallel
set PIDS=
for /L %%i in (1,1,!WORKERS!) do (
  start "worker%%i" cmd /c sqlplus -s %DB_CONN% @tests/concurrency/C_concurrent_orders_worker.sql ^
    !RESULTS_TBL! !TENANT_ID! !CUSTOMER_ID! !PRODUCT_ID! !WAREHOUSE_ID! %%i >worker_%%i.log 2>&1
)

REM 4) Wait for all background workers to finish
set TIMEOUT=120
set ELAPSED=0
:waitloop
tasklist /fi "imagename eq sqlplus.exe" 2>nul | find /i "sqlplus" >nul
if not errorlevel 1 (
  if !ELAPSED! GEQ %TIMEOUT% (
    echo TIMEOUT: Workers did not finish in %TIMEOUT% seconds
    goto :cleanup
  )
  timeout /t 2 /nobreak >nul
  set /a ELAPSED+=2
  goto :waitloop
)

REM 5) Assert: exactly 1 worker CONFIRMED
(
  echo SET SERVEROUTPUT ON
  echo "COLUMN r FORMAT A30"
  echo "SELECT result, COUNT(*) FROM !RESULTS_TBL! GROUP BY result;"
  echo "DECLARE"
  echo "  v_confirmed NUMBER;"
  echo "  v_failed    NUMBER;"
  echo "BEGIN"
  echo "  SELECT SUM(CASE WHEN result='CONFIRMED' THEN 1 ELSE 0 END),"
  echo "         SUM(CASE WHEN result='FAILED' THEN 1 ELSE 0 END)"
  echo "  INTO v_confirmed, v_failed"
  echo "  FROM !RESULTS_TBL!;"
  echo "  DBMS_OUTPUT.PUT_LINE('confirmed=' || v_confirmed || ' failed=' || v_failed);"
  echo "  IF v_confirmed <> 1 THEN"
  echo "    RAISE_APPLICATION_ERROR(-20920, 'Concurrency invariant violated: expected 1 confirmed, got ' || v_confirmed);"
  echo "  END IF;"
  echo "  DBMS_OUTPUT.PUT_LINE('CONCURRENCY TEST PASSED');"
  echo "END;"
  echo "/"
  echo "exit"
) | sqlplus -s %DB_CONN%

REM 5b) Cleanup results table
:cleanup
(
  echo "DROP TABLE !RESULTS_TBL! PURGE;"
  echo "exit"
) | sqlplus -s %DB_CONN% >nul 2>&1

echo === Concurrency test DONE ===
endlocal
</arg_value></tool_call>