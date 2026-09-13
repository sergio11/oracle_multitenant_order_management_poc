--=====================================================================
-- V011 - Multi-tenant isolation via VPD / DBMS_RLS (SRS 14, 93)
--
-- Creates (idempotently where possible):
--   1. Application context APP_CTX (used by pkg_tenant.set_tenant_context)
--   2. PKG_VPD predicate function (fail-closed: 1=0 without context)
--   3. DBMS_RLS policies on all tenant-aware tables
--
-- Pre-requisite (done in infrastructure setup script 01-init-db.sql):
--   GRANT EXECUTE ON SYS.DBMS_RLS TO app_owner;
--   GRANT CREATE ANY CONTEXT TO app_owner;
--
-- The tenant context is set per session by the backend via
-- pkg_tenant.set_tenant_context(tenant_id) before any SQL (SRS 52).
--=====================================================================

--=====================================================================
-- 1) Application context
--=====================================================================
CREATE OR REPLACE CONTEXT app_ctx USING app_owner.pkg_tenant;

--=====================================================================
-- 2) PKG_VPD specification
--=====================================================================
CREATE OR REPLACE PACKAGE pkg_vpd AS
    FUNCTION tenant_predicate(
        p_schema IN VARCHAR2,
        p_object IN VARCHAR2
    ) RETURN VARCHAR2;
END pkg_vpd;
/

--=====================================================================
-- 3) PKG_VPD body
--=====================================================================
CREATE OR REPLACE PACKAGE BODY pkg_vpd AS

    FUNCTION tenant_predicate(
        p_schema IN VARCHAR2,
        p_object IN VARCHAR2
    ) RETURN VARCHAR2 IS
        v_tenant_id VARCHAR2(64);
    BEGIN
        -- Read session tenant context set by pkg_tenant.set_tenant_context
        v_tenant_id := SYS_CONTEXT('APP_CTX', 'TENANT_ID');

        -- FAIL CLOSED: without context, expose no rows (SRS 13).
        IF v_tenant_id IS NULL THEN
            RETURN '1=0';
        END IF;

        RETURN 'tenant_id = TO_NUMBER(''' || v_tenant_id || ''')';
    END tenant_predicate;

END pkg_vpd;
/

--=====================================================================
-- 4) Apply DBMS_RLS policies on all tenant-aware tables
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
    DBMS_RLS.ADD_POLICY('APP_OWNER','IDEMPOTENCY_KEY','POL_IDEMP_TENANT','APP_OWNER','PKG_VPD.TENANT_PREDICATE','SELECT,INSERT,UPDATE,DELETE',TRUE);
END;
/

--=====================================================================
-- Grants for least-privilege users (SRS 58, 59)
--=====================================================================
GRANT EXECUTE ON pkg_vpd TO app_runtime;
GRANT EXECUTE ON pkg_vpd TO app_reporting;
