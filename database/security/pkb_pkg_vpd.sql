--=====================================================================
-- PKG_VPD - Package body
--
-- Implementation of the tenant predicate function (SRS 14).
--
-- Defense in depth is achieved because:
--   - Even if a query omits "WHERE tenant_id = ...", the VPD predicate
--     is appended by Oracle automatically when the policy is active.
--   - If no APP_CTX.TENANT_ID is set in the session, the predicate
--     returns "1=0" so no rows are visible at all (FAIL CLOSED).
--
-- The predicate uses the "parsing user" (VPD executes on the table
-- owner APP_OWNER schema) but SYS_CONTEXT is session-scoped, which is
-- safe: each connection has its own APP_CTX.
--=====================================================================
CREATE OR REPLACE PACKAGE BODY pkg_vpd AS

    FUNCTION tenant_predicate(
        p_schema IN VARCHAR2,
        p_object IN VARCHAR2
    ) RETURN VARCHAR2 IS
        v_tenant_id VARCHAR2(64);
    BEGIN
        -- Read the session tenant context set by pkg_tenant.set_tenant_context
        v_tenant_id := SYS_CONTEXT('APP_CTX', 'TENANT_ID');

        -- Fail closed: without context, expose nothing (SRS 13).
        IF v_tenant_id IS NULL THEN
            RETURN '1=0';
        END IF;

        -- Tenant-scoped predicate (SRS 14, 45).
        RETURN 'tenant_id = TO_NUMBER(''' || v_tenant_id || ''')';
    END tenant_predicate;

END pkg_vpd;
/
</content>