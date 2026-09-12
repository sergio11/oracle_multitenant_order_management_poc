--=====================================================================
-- PKG_AUDIT - Package body
--
-- Implements audit event writing (SRS 36).
-- write_security_event and write_data_change delegate to write_event
-- with the action prefixed to keep a single insertion point.
--=====================================================================
CREATE OR REPLACE PACKAGE BODY pkg_audit AS

    --=================================================================
    -- write_event - core writer
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

-- Grant execute to runtime and reporting users (least privilege, SRS 58/59)
GRANT EXECUTE ON pkg_audit TO app_runtime;
GRANT EXECUTE ON pkg_audit TO app_reporting;
</content>
<task_progress>
- [x] FASE 1-3 completadas
- [x] FASE 4: PKG_TENANT (spec + body)
- [x] FASE 4: PKG_AUDIT spec
- [x] FASE 4: PKG_AUDIT body
- [ ] FASE 4: PKG_INVENTORY (spec + body)
- [ ] FASE 4: PKG_ORDER_MANAGEMENT (spec + body)
- [ ] FASE 4: Commit fase 4
- [ ] FASE 5 a 12 (resto del plan)
</task_progress>