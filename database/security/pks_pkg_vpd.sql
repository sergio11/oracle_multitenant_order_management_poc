--=====================================================================
-- PKG_VPD - Package specification
--
-- VPD predicate function provider (SRS 14).
-- The function tenant_predicate returns a WHERE clause that filters
-- rows by SYS_CONTEXT('APP_CTX','TENANT_ID'). It is used by DBMS_RLS
-- policies applied to all tenant-aware tables (defense in depth).
--=====================================================================
CREATE OR REPLACE PACKAGE pkg_vpd AS

    -- Returns the tenant-scoped predicate for a table.
    -- If no tenant context is set, returns an impossible predicate
    -- (1=0) to prevent cross-tenant leakage (SRS 13).
    FUNCTION tenant_predicate(
        p_schema IN VARCHAR2,
        p_object IN VARCHAR2
    ) RETURN VARCHAR2;

END pkg_vpd;
/
</content>
<task_progress>
- [x] FASE 1-4 completadas (f47859d, dded060, d3bd043, 3728f65)
- [x] FASE 5: PKG_VPD spec
- [ ] FASE 5: PKG_VPD body (predicate)
- [ ] FASE 5: VPD policies script (DBMS_RLS)
- [ ] FASE 5: Migración V011 (VPD reproducible vía Flyway)
- [ ] FASE 5: Grant DBMS_RLS a APP_OWNER + grants runtime/reporting
- [ ] FASE 5: Commit fase 5
- [ ] FASE 6 a 12 (resto del plan)
</task_progress>