--=====================================================================
-- PKG_TENANT - Package specification
--
-- Tenant lifecycle operations (SRS 35).
-- Also provides SET_TENANT_CONTEXT used to establish the Oracle
-- application context for VPD isolation (SRS 14).
--=====================================================================
CREATE OR REPLACE PACKAGE pkg_tenant AS

    -- Creates a new tenant and returns its ID.
    FUNCTION create_tenant(
        p_tenant_code    IN VARCHAR2,
        p_name           IN VARCHAR2,
        p_tier           IN VARCHAR2,
        p_isolation_mode IN VARCHAR2 DEFAULT 'SHARED_SCHEMA'
    ) RETURN NUMBER;

    -- Activates a tenant (status ACTIVE).
    PROCEDURE activate_tenant(
        p_tenant_id IN NUMBER
    );

    -- Suspends a tenant (status SUSPENDED).
    -- Suspended tenants cannot create new orders (FR-003).
    PROCEDURE suspend_tenant(
        p_tenant_id IN NUMBER
    );

    -- Sets the session application context APP_CTX.TENANT_ID for VPD.
    PROCEDURE set_tenant_context(
        p_tenant_id IN NUMBER
    );

    -- Clears the session application context (e.g. on request end).
    PROCEDURE clear_tenant_context;

END pkg_tenant;
/