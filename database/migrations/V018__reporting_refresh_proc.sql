--=====================================================================
-- V018 - Reporting MV refresh procedure (SRS 42, 98)
--
-- Oracle forbids DBMS_MVIEW.REFRESH on MVs whose master tables carry
-- VPD policies (ORA-30372) - except for sessions holding
-- EXEMPT ACCESS POLICY (granted to APP_OWNER in 01-init-db.sql).
-- APP_RUNTIME must NEVER hold that exemption (it would bypass tenant
-- isolation on every query), so refresh goes through this DEFINER's
-- rights procedure: it executes with APP_OWNER's privileges (exempt)
-- while callers only need EXECUTE on the procedure itself.
-- Called by the backend refresh endpoint (ReportingDao) and the IT
-- suite. COMPLETE refresh ON DEMAND, same as the MV definitions.
--=====================================================================
CREATE OR REPLACE PROCEDURE refresh_reporting_views AS
BEGIN
    DBMS_MVIEW.REFRESH('MV_ORDER_DAILY_SUMMARY', 'C');
    DBMS_MVIEW.REFRESH('MV_PRODUCT_SALES_SUMMARY', 'C');
END refresh_reporting_views;
/

GRANT EXECUTE ON refresh_reporting_views TO app_runtime;
GRANT EXECUTE ON refresh_reporting_views TO app_reporting;
