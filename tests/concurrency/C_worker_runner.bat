@echo off
REM =====================================================================
REM Concurrency test worker runner (one per worker).
REM
REM Runs sqlplus for a single worker and ALWAYS drops a .done marker
REM afterwards so run_concurrency_test.bat can wait for ITS OWN workers
REM (instead of waiting on any sqlplus.exe on the machine).
REM
REM Args:
REM   %1 sqlplus connect string (user/pass@//host/pdb)
REM   %2 worker_id
REM   %3 tenant_id
REM   %4 customer_id
REM   %5 product_id
REM   %6 warehouse_id
REM   %7 results_table
REM =====================================================================
sqlplus -s "%~1" @tests/concurrency/C_concurrent_orders_worker.sql %2 %3 %4 %5 %6 %7 >worker_%2.log 2>&1
set RC=%errorlevel%
echo done>worker_%2.done
exit /b %RC%
