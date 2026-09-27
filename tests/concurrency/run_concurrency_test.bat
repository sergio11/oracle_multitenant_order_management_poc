@echo off
REM =====================================================================
REM Concurrency test orchestrator (SRS §37, §93, FT-003, FT-004)
REM
REM Sets up a single row of stock for one product/warehouse (qty=1),
REM launches N workers in parallel, waits for THEIR done-markers (not
REM for every sqlplus.exe on the machine), then asserts exactly 1
REM succeeded and the stock reservation is consistent.
REM
REM Requires sqlplus on PATH, run from the repository root.
REM Connection defaults match infrastructure/podman/.env.example.
REM =====================================================================
setlocal enabledelayedexpansion

set DB_USER_SRC=set
set DB_PASS_SRC=set
set DB_CONN_SRC=set
set DB_OWNER_USER_SRC=set
set DB_OWNER_PASS_SRC=set
set DB_OWNER_CONN_SRC=set
if not defined DB_USER (set "DB_USER=app_runtime" & set "DB_USER_SRC=defaulted")
if not defined DB_PASS (set "DB_PASS=LabRuntimePass1" & set "DB_PASS_SRC=defaulted")
if not defined DB_CONN (set "DB_CONN=!DB_USER!/!DB_PASS!@//localhost:1521/pdb_oltp" & set "DB_CONN_SRC=defaulted")
if not defined DB_OWNER_USER (set "DB_OWNER_USER=app_owner" & set "DB_OWNER_USER_SRC=defaulted")
if not defined DB_OWNER_PASS (set "DB_OWNER_PASS=LabOwnerPass1" & set "DB_OWNER_PASS_SRC=defaulted")
if not defined DB_OWNER_CONN (set "DB_OWNER_CONN=!DB_OWNER_USER!/!DB_OWNER_PASS!@//localhost:1521/pdb_oltp" & set "DB_OWNER_CONN_SRC=defaulted")
if not defined TENANT_ID set TENANT_ID=1
if not defined CUSTOMER_ID set CUSTOMER_ID=1
if not defined PRODUCT_ID set PRODUCT_ID=1
if not defined WAREHOUSE_ID set WAREHOUSE_ID=1
if not defined WORKERS set WORKERS=5

REM Warn when any DB_* connection var fell back to its default (passwords redacted).
set "DEFAULTED_DB_VARS="
if "!DB_USER_SRC!"=="defaulted" set "DEFAULTED_DB_VARS=!DEFAULTED_DB_VARS! DB_USER"
if "!DB_PASS_SRC!"=="defaulted" set "DEFAULTED_DB_VARS=!DEFAULTED_DB_VARS! DB_PASS"
if "!DB_CONN_SRC!"=="defaulted" set "DEFAULTED_DB_VARS=!DEFAULTED_DB_VARS! DB_CONN"
if "!DB_OWNER_USER_SRC!"=="defaulted" set "DEFAULTED_DB_VARS=!DEFAULTED_DB_VARS! DB_OWNER_USER"
if "!DB_OWNER_PASS_SRC!"=="defaulted" set "DEFAULTED_DB_VARS=!DEFAULTED_DB_VARS! DB_OWNER_PASS"
if "!DB_OWNER_CONN_SRC!"=="defaulted" set "DEFAULTED_DB_VARS=!DEFAULTED_DB_VARS! DB_OWNER_CONN"
if defined DEFAULTED_DB_VARS (
  echo =============================================================
  echo WARNING: using DEFAULT DB connection values for:!DEFAULTED_DB_VARS!
  echo WARNING: set DB_USER/DB_PASS/DB_CONN/DB_OWNER_USER/DB_OWNER_PASS/DB_OWNER_CONN explicitly for non-local runs.
  echo DB_USER=!DB_USER! [!DB_USER_SRC!]
  echo DB_PASS=^<!DB_PASS_SRC!^>
  echo DB_CONN=^<!DB_CONN_SRC!^>
  echo DB_OWNER_USER=!DB_OWNER_USER! [!DB_OWNER_USER_SRC!]
  echo DB_OWNER_PASS=^<!DB_OWNER_PASS_SRC!^>
  echo DB_OWNER_CONN=^<!DB_OWNER_CONN_SRC!^>
  echo =============================================================
)

REM Fail fast on empty required IDs after defaulting.
if "!TENANT_ID!"=="" (echo ERROR: TENANT_ID is empty. Set TENANT_ID explicitly. & exit /b 1)
if "!CUSTOMER_ID!"=="" (echo ERROR: CUSTOMER_ID is empty. Set CUSTOMER_ID explicitly. & exit /b 1)
if "!PRODUCT_ID!"=="" (echo ERROR: PRODUCT_ID is empty. Set PRODUCT_ID explicitly. & exit /b 1)
if "!WAREHOUSE_ID!"=="" (echo ERROR: WAREHOUSE_ID is empty. Set WAREHOUSE_ID explicitly. & exit /b 1)
REM WORKERS has a default (5); validate it is a positive integer.
echo !WORKERS!| findstr /R "^[0-9][0-9]*$" >nul
if errorlevel 1 (echo ERROR: WORKERS must be a positive integer, got '!WORKERS!'. & exit /b 1)
if !WORKERS! LSS 1 (echo ERROR: WORKERS must be ^>= 1, got '!WORKERS!'. & exit /b 1)

set RESULTS_TBL=concurrency_results_%RANDOM%
set TEST_RC=1

echo === Concurrency test: !WORKERS! workers, stock qty=1 ===

REM Clear stale done-markers from previous runs
del /q worker_*.done 2>nul

REM 1) Create results table as APP_OWNER, grant runtime access
(
  echo WHENEVER SQLERROR EXIT SQL.SQLCODE
  echo CREATE TABLE !RESULTS_TBL! ^(worker_id NUMBER, order_id NUMBER, result VARCHAR2^(30^), error_msg VARCHAR2^(4000^), ts TIMESTAMP^);
  echo GRANT SELECT, INSERT ON !RESULTS_TBL! TO app_runtime;
  echo commit;
  echo exit
) | sqlplus -s "%DB_OWNER_CONN%"
if errorlevel 1 (
  echo ERROR: failed to create results table !RESULTS_TBL!
  exit /b 1
)

REM 2) Reset stock for the product to exactly 1 unit (pessimistic).
REM    Separate .sql file (no cmd quoting hazards); errors are shown.
REM    Closure fix (V023): setup runs as APP_OWNER - APP_RUNTIME no
REM    longer holds EXECUTE on pkg_inventory (set_test_stock).
sqlplus -s "%DB_OWNER_CONN%" @tests/concurrency/C_setup_stock.sql %TENANT_ID% %PRODUCT_ID% %WAREHOUSE_ID% 1
if errorlevel 1 (
  echo ERROR: stock setup failed
  goto :cleanup_table
)

REM 3) Launch N workers in parallel (each drops worker_N.done at the end)
for /L %%i in (1,1,!WORKERS!) do (
  start "worker%%i" tests\concurrency\C_worker_runner.bat "%DB_CONN%" %%i !TENANT_ID! !CUSTOMER_ID! !PRODUCT_ID! !WAREHOUSE_ID! !RESULTS_TBL!
)

REM 4) Wait for OUR workers' done-markers (per-worker, not machine-wide)
set TIMEOUT=120
set ELAPSED=0
:waitloop
set ALL_DONE=1
for /L %%i in (1,1,!WORKERS!) do (
  if not exist worker_%%i.done set ALL_DONE=0
)
if "!ALL_DONE!"=="0" (
  if !ELAPSED! GEQ %TIMEOUT% goto :timeout_wait
  timeout /t 2 /nobreak >nul
  set /a ELAPSED+=2
  goto :waitloop
)
goto :assert

:timeout_wait
echo TIMEOUT: workers did not finish in %TIMEOUT% seconds
goto :cleanup_table

:assert
REM 5) Assert: exactly 1 worker CONFIRMED (as APP_OWNER)
sqlplus -s "%DB_OWNER_CONN%" @tests/concurrency/C_assert_results.sql !RESULTS_TBL!
if errorlevel 1 (
  echo CONCURRENCY TEST FAILED
  set TEST_RC=1
) else (
  set TEST_RC=0
)
:cleanup_table
REM 5b) Cleanup results table (APP_OWNER owns it)
(
  echo DROP TABLE !RESULTS_TBL! PURGE;
  echo exit
) | sqlplus -s "%DB_OWNER_CONN%" >nul 2>&1

REM 5c) Cleanup markers (keep worker_*.log for debugging)
del /q worker_*.done 2>nul

if "!TEST_RC!"=="0" (
  echo === Concurrency test DONE ===
) else (
  echo === Concurrency test DONE [FAILED] ===
)
exit /b !TEST_RC!
