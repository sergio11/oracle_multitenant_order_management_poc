@echo off
REM R02 — DB outage: controlled 503 + Unready-no-restart + recovery (FT-001).
REM UNEXECUTED in this pass. kind flow mirrors root README FT-001 section.
REM Run from the repository root.
setlocal
if not defined BACKEND_URL set BACKEND_URL=http://localhost:8080
if not defined DB_CONN set "DB_CONN=app_runtime/LabRuntimePass1@//localhost:1521/pdb_oltp"
echo 1) Scaling oracle to 0...
kubectl scale statefulset oracle -n poc-qa --replicas=0
echo 2) Expect controlled 503 JSON (~30s pool timeout, no hang):
curl -s -X POST %BACKEND_URL%/api/v1/orders -H "Content-Type: application/json" -H "X-Tenant-ID: ACME" -u operator:changeme -d "{\"customerId\":1,\"currency\":\"USD\"}"
echo.
echo 3) Expect pod Unready but NOT restarted: kubectl get pods -n poc-qa
kubectl get pods -n poc-qa
echo 4) Restoring oracle...
kubectl scale statefulset oracle -n poc-qa --replicas=1
echo 5) After qa:wait, recovery probe:
sqlplus -S "%DB_CONN%" @tests/resilience/R02_db_outage.sql
