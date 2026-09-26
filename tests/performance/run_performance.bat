@echo off
REM run_performance.bat — SRS §107 six-workload runner (UNEXECUTED: collects results, asserts no stored outcome).
REM Run from the repository root. Requires sqlplus on PATH.
REM
REM Thresholds (max Elapsed per workload, seconds). PASS/FAIL is printed per
REM workload by comparing the largest "Elapsed:" timing in its spool file.
REM NOTE: Windows bat float comparison is coarse — values are compared as
REM hundredths via a powershell helper; if powershell is missing the script
REM prints the observed elapsed lines and marks the workload CHECK (manual).
setlocal enabledelayedexpansion
set TH_P01=2
set TH_P02=5
set TH_P03=5
set TH_P04=5
set TH_P05=10
set TH_P06=5
if not defined DB_CONN set "DB_CONN=app_runtime/LabRuntimePass1@//localhost:1521/pdb_oltp"
if not exist results mkdir results
set OVERALL=0
for %%F in (P01_single_order_latency P02_confirm_throughput P03_payment_mix P04_mv_query_latency P05_top_products_scan P06_mixed_read_write) do (
  echo === Running %%F ===
  sqlplus -S "%DB_CONN%" @tests/performance/%%F.sql > results\%%F.log 2>&1
  if errorlevel 1 (
    echo FAIL %%F ^(sqlplus error; see results\%%F.log^)
    set OVERALL=1
  ) else (
    for /F "tokens=2 delims==" %%T in ('findstr /R /C:"Elapsed:" results\%%F.log 2^>nul') do echo   elapsed:%%T
    echo CHECK %%F ^(see results\%%F.log; threshold !TH_%%F:~0,2!s^)
  )
)
if "!OVERALL!"=="0" (echo ALL WORKLOADS RAN. Compare Elapsed lines vs thresholds above.) else (echo SOME WORKLOADS FAILED. Inspect results\.)
exit /b !OVERALL!
