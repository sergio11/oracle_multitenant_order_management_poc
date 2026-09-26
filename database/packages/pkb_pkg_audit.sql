--=====================================================================
-- PKG_AUDIT - Package body
--
-- Implements audit event writing (SRS 36).
-- write_security_event and write_data_change delegate to write_event
-- with the action prefixed to keep a single insertion point.
--
-- NOTE (autonomous audit): write_event runs as an AUTONOMOUS_TRANSACTION
-- and COMMITs on success, so the audit row survives even if the caller
-- rolls back its business transaction. Exceptions still propagate to
-- the caller unchanged (the autonomous work rolls back automatically).
--
-- NOTE (tenant_id): every writer (pkg_tenant.create_tenant,
-- pkg_order_management.transition_status) always supplies a non-NULL
-- tenant_id - no direct audit_event INSERTs exist in packages or seed -
-- so the V007 tenant_id NOT NULL constraint is never violated.
--=====================================================================
CREATE OR REPLACE PACKAGE BODY pkg_audit AS

    --=================================================================
    -- write_event - core writer
    --
    -- NOTE: autonomous transaction - the INSERT below is COMMITted on
    -- success so the audit row survives a caller rollback; failures
    -- propagate to the caller unchanged (callers need no changes).
    --=================================================================
    PROCEDURE write_event(
        p_tenant_id       IN NUMBER,
        p_entity_type     IN VARCHAR2,
        p_entity_id       IN VARCHAR2,
        p_action          IN VARCHAR2,
        p_actor           IN VARCHAR2 DEFAULT 'SYSTEM',
        p_correlation_id  IN VARCHAR2 DEFAULT NULL,
        p_payload         IN CLOB      DEFAULT NULL
    ) IS
        PRAGMA AUTONOMOUS_TRANSACTION;
    BEGIN
        INSERT INTO audit_event (
            tenant_id,
            entity_type,
            entity_id,
            action,
            actor,
            correlation_id,
            payload
        ) VALUES (
            p_tenant_id,
            p_entity_type,
            p_entity_id,
            p_action,
            p_actor,
            p_correlation_id,
            p_payload
        );
        COMMIT;
    END write_event;

    --=================================================================
    -- write_security_event
    --=================================================================
    PROCEDURE write_security_event(
        p_tenant_id       IN NUMBER,
        p_entity_type     IN VARCHAR2,
        p_entity_id       IN VARCHAR2,
        p_action          IN VARCHAR2,
        p_actor           IN VARCHAR2 DEFAULT 'SYSTEM',
        p_correlation_id  IN VARCHAR2 DEFAULT NULL,
        p_payload         IN CLOB      DEFAULT NULL
    ) IS
    BEGIN
        write_event(
            p_tenant_id       => p_tenant_id,
            p_entity_type     => p_entity_type,
            p_entity_id       => p_entity_id,
            p_action          => 'SECURITY_' || p_action,
            p_actor           => p_actor,
            p_correlation_id  => p_correlation_id,
            p_payload         => p_payload
        );
    END write_security_event;

    --=================================================================
    -- write_data_change
    --=================================================================
    PROCEDURE write_data_change(
        p_tenant_id       IN NUMBER,
        p_entity_type     IN VARCHAR2,
        p_entity_id       IN VARCHAR2,
        p_action          IN VARCHAR2,
        p_actor           IN VARCHAR2 DEFAULT 'SYSTEM',
        p_correlation_id  IN VARCHAR2 DEFAULT NULL,
        p_payload         IN CLOB      DEFAULT NULL
    ) IS
    BEGIN
        write_event(
            p_tenant_id       => p_tenant_id,
            p_entity_type     => p_entity_type,
            p_entity_id       => p_entity_id,
            p_action          => 'DATA_CHANGE_' || p_action,
            p_actor           => p_actor,
            p_correlation_id  => p_correlation_id,
            p_payload         => p_payload
        );
    END write_data_change;

END pkg_audit;
/

-- Grant execute to runtime user (least privilege, SRS 58/59)
-- NOTE (C2): app_reporting is read-only - no EXECUTE here (see V019).
GRANT EXECUTE ON pkg_audit TO app_runtime;