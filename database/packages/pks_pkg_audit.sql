--=====================================================================
-- PKG_AUDIT - Package specification
--
-- Audit events (SRS 36). Writes functional, security and data-change
-- events used to reconstruct relevant operations (SRS 27).
--=====================================================================
CREATE OR REPLACE PACKAGE pkg_audit AS

    -- Generic audit event writer.
    PROCEDURE write_event(
        p_tenant_id       IN NUMBER,
        p_entity_type     IN VARCHAR2,
        p_entity_id       IN VARCHAR2,
        p_action          IN VARCHAR2,
        p_actor           IN VARCHAR2 DEFAULT 'SYSTEM',
        p_correlation_id  IN VARCHAR2 DEFAULT NULL,
        p_payload         IN CLOB      DEFAULT NULL
    );

    -- Security-related audit event (e.g. denied cross-tenant access).
    PROCEDURE write_security_event(
        p_tenant_id       IN NUMBER,
        p_entity_type     IN VARCHAR2,
        p_entity_id       IN VARCHAR2,
        p_action          IN VARCHAR2,
        p_actor           IN VARCHAR2 DEFAULT 'SYSTEM',
        p_correlation_id  IN VARCHAR2 DEFAULT NULL,
        p_payload         IN CLOB      DEFAULT NULL
    );

    -- Data-change audit event (e.g. order status transition).
    PROCEDURE write_data_change(
        p_tenant_id       IN NUMBER,
        p_entity_type     IN VARCHAR2,
        p_entity_id       IN VARCHAR2,
        p_action          IN VARCHAR2,
        p_actor           IN VARCHAR2 DEFAULT 'SYSTEM',
        p_correlation_id  IN VARCHAR2 DEFAULT NULL,
        p_payload         IN CLOB      DEFAULT NULL
    );

END pkg_audit;
/