--=====================================================================
-- PKG_TENANT - Package body
--
-- Implements tenant lifecycle (SRS 35).
-- Uses explicit error codes for clean mapping to API errors (SRS 54).
--=====================================================================
CREATE OR REPLACE PACKAGE BODY pkg_tenant AS

    --=================================================================
    -- create_tenant
    --=================================================================
    FUNCTION create_tenant(
        p_tenant_code    IN VARCHAR2,
        p_name           IN VARCHAR2,
        p_tier           IN VARCHAR2,
        p_isolation_mode IN VARCHAR2 DEFAULT 'SHARED_SCHEMA'
    ) RETURN NUMBER IS
        v_tenant_id NUMBER;
        v_code_conflict EXCEPTION;
        PRAGMA EXCEPTION_INIT(v_code_conflict, -20001);
    BEGIN
        -- Validate tier
        IF p_tier NOT IN ('STANDARD','PREMIUM','ENTERPRISE') THEN
            RAISE_APPLICATION_ERROR(-20002, 'Invalid TIER. Use STANDARD, PREMIUM or ENTERPRISE.');
        END IF;

        -- Validate isolation mode
        IF p_isolation_mode NOT IN ('SHARED_SCHEMA','DEDICATED_SCHEMA','DEDICATED_PDB') THEN
            RAISE_APPLICATION_ERROR(-20003, 'Invalid ISOLATION_MODE.');
        END IF;

        BEGIN
            INSERT INTO tenant (tenant_code, name, tier, status, isolation_mode)
            VALUES (p_tenant_code, p_name, p_tier, 'ACTIVE', p_isolation_mode)
            RETURNING tenant_id INTO v_tenant_id;
        EXCEPTION
            WHEN DUP_VAL_ON_INDEX THEN
                RAISE_APPLICATION_ERROR(-20001, 'Tenant code already exists: ' || p_tenant_code);
        END;

        -- Audit tenant creation (SRS 27, FR-013)
        pkg_audit.write_event(
            p_tenant_id   => v_tenant_id,
            p_entity_type => 'TENANT',
            p_entity_id   => TO_CHAR(v_tenant_id),
            p_action      => 'CREATE_TENANT',
            p_actor       => 'APP_JAVA',
            p_payload     => JSON_OBJECT(
                                 KEY 'code' VALUE p_tenant_code,
                                 KEY 'tier' VALUE p_tier)
        );

        RETURN v_tenant_id;
    END create_tenant;

    --=================================================================
    -- activate_tenant
    --=================================================================
    PROCEDURE activate_tenant(p_tenant_id IN NUMBER) IS
    BEGIN
        UPDATE tenant
           SET status = 'ACTIVE',
               updated_at = SYSTIMESTAMP
         WHERE tenant_id = p_tenant_id;

        IF SQL%ROWCOUNT = 0 THEN
            RAISE_APPLICATION_ERROR(-20004, 'Tenant not found: ' || p_tenant_id);
        END IF;
    END activate_tenant;

    --=================================================================
    -- suspend_tenant
    --=================================================================
    PROCEDURE suspend_tenant(p_tenant_id IN NUMBER) IS
    BEGIN
        UPDATE tenant
           SET status = 'SUSPENDED',
               updated_at = SYSTIMESTAMP
         WHERE tenant_id = p_tenant_id;

        IF SQL%ROWCOUNT = 0 THEN
            RAISE_APPLICATION_ERROR(-20004, 'Tenant not found: ' || p_tenant_id);
        END IF;
    END suspend_tenant;

    --=================================================================
    -- set_tenant_context
    -- Sets APP_CTX.TENANT_ID for the current session.
    -- VPD policies read SYS_CONTEXT('APP_CTX','TENANT_ID') (SRS 14).
    --=================================================================
    PROCEDURE set_tenant_context(p_tenant_id IN NUMBER) IS
    BEGIN
        DBMS_SESSION.SET_CONTEXT(
            namespace => 'APP_CTX',
            attribute => 'TENANT_ID',
            value     => TO_CHAR(p_tenant_id)
        );
    END set_tenant_context;

    --=================================================================
    -- clear_tenant_context
    --=================================================================
    PROCEDURE clear_tenant_context IS
    BEGIN
        DBMS_SESSION.CLEAR_ALL_CONTEXT('APP_CTX');
    END clear_tenant_context;

END pkg_tenant;
/

-- Grant execute to the runtime user only (least privilege, SRS 58/59).
-- Closure fix (C3): the former EXECUTE grant to app_reporting is
-- removed - pkg_tenant carries create/activate/suspend lifecycle
-- procedures and the read-only reporting user must not hold them
-- (revoked on existing DBs by V023).
GRANT EXECUTE ON pkg_tenant TO app_runtime;