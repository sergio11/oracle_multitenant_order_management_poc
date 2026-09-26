@echo off
REM R01 — backend kill/restart recovery (SRS §117, FT-001 adjacent).
REM Restarts the backend container (compose lab) and asserts recovery via
REM GET /internal/health. UNEXECUTED in this pass.
REM Run from the repository root.
setlocal
if not defined BACKEND_CONTAINER set BACKEND_CONTAINER=poc-backend
if not defined HEALTH_URL set HEALTH_URL=http://localhost:8080/internal/health
echo Stopping %BACKEND_CONTAINER%...
podman stop %BACKEND_CONTAINER%
if errorlevel 1 (echo ERROR: podman stop failed. & exit /b 1)
podman start %BACKEND_CONTAINER%
if errorlevel 1 (echo ERROR: podman start failed. & exit /b 1)
echo Waiting for health at %HEALTH_URL%...
for /L %%i in (1,1,30) do (
  curl -sf %HEALTH_URL% >nul 2>&1
  if not errorlevel 1 (echo RECOVERED: backend healthy. & exit /b 0)
  timeout /t 5 /nobreak >nul
)
echo FAILED: backend did not become healthy in time.
exit /b 1
