-- R02_db_outage.sql — probe queries used during the outage window.
-- Run via rake qa:resilience:db_outage. Expects: reads succeed/fail-closed per VPD only
-- when the DB is UP; when DOWN the app (not this script) returns 503 JSON.
-- This script simply proves the DB is reachable again after recovery.
WHENEVER SQLERROR EXIT FAILURE
SELECT tenant_code, status FROM tenant WHERE tenant_code IN ('ACME','GLOBEX');
SELECT COUNT(*) AS orders_visible FROM sales_order;
