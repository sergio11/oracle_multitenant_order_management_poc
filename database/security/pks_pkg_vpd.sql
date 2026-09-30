--=====================================================================
-- *** REFERENCE ONLY - NOT DEPLOYED BY deploy_packages.sql ***
-- The LIVE definition of pkg_vpd (spec + body) is created solely by
-- the Flyway migration database/migrations/V011__create_vpd.sql.
-- database/packages/deploy_packages.sql does NOT include this file, so
-- editing it has NO effect. If you change anything here, change V011
-- too (or edit V011 only and treat this file as a readable copy).
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