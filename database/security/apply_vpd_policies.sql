--=====================================================================
-- apply_vpd_policies.sql
--
-- Applies DBMS_RLS (Virtual Private Database) policies to all
-- tenant-aware tables (SRS 14, 93).
--
-- Uses pkg_vpd.tenant_predicate as the predicate function. Each policy
-- appends "tenant_id = <APP_CTX.TENANT_ID>" automatically to queries,
-- providing defense in depth on top of application-level tenant scoping.
--
-- Run as APP_OWNER after V011 migration + package deployment:
--   sqlplus app_owner/<pwd>@//localhost:1521/pdb_oltp @database/security/apply_vpd_policies.sql
--
-- NOTE: Reflects the exact policy set that V011 migration also creates.
--=====================================================================

-- Ensure all policies are dropped first (idempotent re-runs).
BEGIN
    DBMS_RLS.DROP_POLICY('APP_OWNER','CUSTOMER','POL_CUSTOMER_TENANT');
    DBMS_RLS.DROP_POLICY('APP_OWNER','CUSTOMER_ADDRESS','POL_ADDR_TENANT');
    DBMS_RLS.DROP_POLICY('APP_OWNER','CUSTOMER_ACCOUNT','POL_ACCT_TENANT');
    DBMS_RLS.DROP_POLICY('APP_OWNER','PRODUCT_CATEGORY','POL_CATEGORY_TENANT');
    DBMS_RLS.DROP_POLICY('APP_OWNER','PRODUCT','POL_PRODUCT_TENANT');
    DBMS_RLS.DROP_POLICY('APP_OWNER','WAREHOUSE','POL_WAREHOUSE_TENANT');
    DBMS_RLS.DROP_POLICY('APP_OWNER','INVENTORY','POL_INVENTORY_TENANT');
    DBMS_RLS.DROP_POLICY('APP_OWNER','SALES_ORDER','POL_ORDER_TENANT');
    DBMS_RLS.DROP_POLICY('APP_OWNER','SALES_ORDER_LINE','POL_LINE_TENANT');
    DBMS_RLS.DROP_POLICY('APP_OWNER','ORDER_STATUS_HISTORY','POL_HISTORY_TENANT');
    DBMS_RLS.DROP_POLICY('APP_OWNER','PAYMENT','POL_PAYMENT_TENANT');
    DBMS_RLS.DROP_POLICY('APP_OWNER','OUTBOX_EVENT','POL_OUTBOX_TENANT');
EXCEPTION
    WHEN OTHERS THEN
        -- Ignore "policy does not exist" errors on re-run
        NULL;
END;
/

--=====================================================================
-- Add static policies (statement_type 'SELECT,INSERT,UPDATE,DELETE')
--=====================================================================
BEGIN
    DBMS_RLS.ADD_POLICY('APP_OWNER','CUSTOMER',          'POL_CUSTOMER_TENANT','APP_OWNER','PKG_VPD.TENANT_PREDICATE','SELECT,INSERT,UPDATE,DELETE',TRUE);
    DBMS_RLS.ADD_POLICY('APP_OWNER','CUSTOMER_ADDRESS',  'POL_ADDR_TENANT',    'APP_OWNER','PKG_VPD.TENANT_PREDICATE','SELECT,INSERT,UPDATE,DELETE',TRUE);
    DBMS_RLS.ADD_POLICY('APP_OWNER','CUSTOMER_ACCOUNT',  'POL_ACCT_TENANT',    'APP_OWNER','PKG_VPD.TENANT_PREDICATE','SELECT,INSERT,UPDATE,DELETE',TRUE);
    DBMS_RLS.ADD_POLICY('APP_OWNER','PRODUCT_CATEGORY',  'POL_CATEGORY_TENANT','APP_OWNER','PKG_VPD.TENANT_PREDICATE','SELECT,INSERT,UPDATE,DELETE',TRUE);
    DBMS_RLS.ADD_POLICY('APP_OWNER','PRODUCT',           'POL_PRODUCT_TENANT', 'APP_OWNER','PKG_VPD.TENANT_PREDICATE','SELECT,INSERT,UPDATE,DELETE',TRUE);
    DBMS_RLS.ADD_POLICY('APP_OWNER','WAREHOUSE',         'POL_WAREHOUSE_TENANT','APP_OWNER','PKG_VPD.TENANT_PREDICATE','SELECT,INSERT,UPDATE,DELETE',TRUE);
    DBMS_RLS.ADD_POLICY('APP_OWNER','INVENTORY',         'POL_INVENTORY_TENANT','APP_OWNER','PKG_VPD.TENANT_PREDICATE','SELECT,INSERT,UPDATE,DELETE',TRUE);
    DBMS_RLS.ADD_POLICY('APP_OWNER','SALES_ORDER',       'POL_ORDER_TENANT',   'APP_OWNER','PKG_VPD.TENANT_PREDICATE','SELECT,INSERT,UPDATE,DELETE',TRUE);
    DBMS_RLS.ADD_POLICY('APP_OWNER','SALES_ORDER_LINE',  'POL_LINE_TENANT',    'APP_OWNER','PKG_VPD.TENANT_PREDICATE','SELECT,INSERT,UPDATE,DELETE',TRUE);
    DBMS_RLS.ADD_POLICY('APP_OWNER','ORDER_STATUS_HISTORY','POL_HISTORY_TENANT','APP_OWNER','PKG_VPD.TENANT_PREDICATE','SELECT,INSERT,UPDATE,DELETE',TRUE);
    DBMS_RLS.ADD_POLICY('APP_OWNER','PAYMENT',           'POL_PAYMENT_TENANT', 'APP_OWNER','PKG_VPD.TENANT_PREDICATE','SELECT,INSERT,UPDATE,DELETE',TRUE);
    DBMS_RLS.ADD_POLICY('APP_OWNER','OUTBOX_EVENT',      'POL_OUTBOX_TENANT',  'APP_OWNER','PKG_VPD.TENANT_PREDICATE','SELECT,INSERT,UPDATE,DELETE',TRUE);
END;
/

PROMPT VPD policies applied successfully.
</content>
<task_progress>
- [x] FASE 1-4 completadas (f47859d, dded060, d3bd043, 3728f65)
- [x] FASE 5: PKG_VPD spec + body
- [x] FASE 5: apply_vpd_policies.sql (DBMS_RLS)
- [ ] FASE 5: Migración V011 (VPD reproducible vía Flyway)
- [ ] FASE 5: Grant DBMS_RLS a APP_OWNER en script init/backup
- [ ] FASE 5: Commit fase 5
- [ ] FASE 6 a 12 (resto del plan)
</task_progress>