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