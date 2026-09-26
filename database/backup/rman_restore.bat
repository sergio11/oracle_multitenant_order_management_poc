@echo off
REM rman_restore.bat — restore CDB + PDB_OLTP from rman_full_backup pieces.
REM NOT executed live in this lab. Run from the repository root.
REM Requires: RMAN on PATH, ORACLE_SID set, /backup reachable.
setlocal
if "%ORACLE_SID%"=="" (echo ERROR: ORACLE_SID is not set. & exit /b 1)
where rman >nul 2>&1
if errorlevel 1 (echo ERROR: rman not found on PATH. & exit /b 1)
if not exist database\backup\rman_restore_body.rcv (echo ERROR: rman_restore_body.rcv missing. & exit /b 1)
echo Pre-checks OK. SID=%ORACLE_SID%. Starting restore...
rman target / @database\backup\rman_restore_body.rcv
if errorlevel 1 (echo RESTORE FAILED. & exit /b 1)
echo RESTORE DONE. Open PDB_OLTP and verify with backup_verify.sql.
