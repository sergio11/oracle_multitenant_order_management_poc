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
<task_progress>
- [x] FASE 1-4 completadas (f47859d, dded060, d3bd043, 3728f65)
- [x] FASE 5: PKG_VPD spec
- [x] FASE 5: PKG_VPD body (predicate)
- [ ] FASE 5: VPD policies script (DBMS_RLS)
- [ ] FASE 5: Migración V011 (VPD reproducible vía Flyway)
- [ ] FASE 5: Grant DBMS_RLS a APP_OWNER + grants runtime/reporting
- [ ] FASE 5: Commit fase 5
- [ ] FASE 6 a 12 (resto del plan)
</task_progress>