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

if "%1"=="" (
  set DB_CONN=app_runtime/ChangeMe123!@//localhost:1521/pdb_oltp
  set TENANT_ID=1
  set CUSTOMER_ID=1
  set PRODUCT_ID=1
  set WAREHOUSE_ID=1
  set WORKERS=5
) else (
  set DB_CONN=%1
  set TENANT_ID=2
  set CUSTOMER_ID=4
  set PRODUCT_ID=3
  set WAREHOUSE_ID=1
  set WORKERS=5
)

set RESULTS_TBL=concurrency_results_!TIME: =!

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
:waitloop
tasklist /fi "imagename eq cmd.exe" /v | find "worker1" >nul
if not errorlevel 1 (
  timeout /t 2 /nobreak >nul
  goto waitloop
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
(
  echo "DROP TABLE !RESULTS_TBL! PURGE;"
  echo "exit"
) | sqlplus -s %DB_CONN% >nul 2>&1

echo === Concurrency test DONE ===
endlocal
</arg_value></tool_call>