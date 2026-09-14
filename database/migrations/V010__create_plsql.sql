--=====================================================================
-- V010 - PL/SQL Business Logic (SRS 92, 60)
--
-- Flyway-valid migration. Creates the sequence used for order numbers
-- and the SPECIFICATIONS of the business logic packages:
--   PKG_TENANT, PKG_AUDIT, PKG_INVENTORY, PKG_ORDER_MANAGEMENT
--
-- The canonical source of package bodies lives in database/packages/.
-- They are deployed via database/packages/deploy_packages.sql AFTER
-- this migration completes (see README "Packages deployment").
--
-- Flyway does not support `@file` includes; splitting specs (here) from
-- bodies (deploy script) keeps a single, maintainable source of truth.
--=====================================================================

--=====================================================================
-- Order number sequence (generated as SO-<tenant>-<seq>)
--=====================================================================
CREATE SEQUENCE tenant_ord_seq
    START WITH 10000
    INCREMENT BY 1
    CACHE 20
    NOCYCLE;

--=====================================================================
-- PKG_AUDIT specification
-- (compiled first - PKG_TENANT and PKG_ORDER_MANAGEMENT depend on it)
--=====================================================================
CREATE OR REPLACE PACKAGE pkg_audit AS
    PROCEDURE write_event(
        p_tenant_id       IN NUMBER,
        p_entity_type     IN VARCHAR2,
        p_entity_id       IN VARCHAR2,
        p_action          IN VARCHAR2,
        p_actor           IN VARCHAR2 DEFAULT 'SYSTEM',
        p_correlation_id  IN VARCHAR2 DEFAULT NULL,
        p_payload         IN CLOB      DEFAULT NULL
    );

    PROCEDURE write_security_event(
        p_tenant_id       IN NUMBER,
        p_entity_type     IN VARCHAR2,
        p_entity_id       IN VARCHAR2,
        p_action          IN VARCHAR2,
        p_actor           IN VARCHAR2 DEFAULT 'SYSTEM',
        p_correlation_id  IN VARCHAR2 DEFAULT NULL,
        p_payload         IN CLOB      DEFAULT NULL
    );

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

--=====================================================================
-- PKG_TENANT specification
--=====================================================================
CREATE OR REPLACE PACKAGE pkg_tenant AS
    FUNCTION create_tenant(
        p_tenant_code    IN VARCHAR2,
        p_name           IN VARCHAR2,
        p_tier           IN VARCHAR2,
        p_isolation_mode IN VARCHAR2 DEFAULT 'SHARED_SCHEMA'
    ) RETURN NUMBER;

    PROCEDURE activate_tenant(p_tenant_id IN NUMBER);

    PROCEDURE suspend_tenant(p_tenant_id IN NUMBER);

    PROCEDURE set_tenant_context(p_tenant_id IN NUMBER);

    PROCEDURE clear_tenant_context;
END pkg_tenant;
/

--=====================================================================
-- PKG_INVENTORY specification
--=====================================================================
CREATE OR REPLACE PACKAGE pkg_inventory AS
    FUNCTION get_available_stock(
        p_tenant_id    IN NUMBER,
        p_warehouse_id IN NUMBER,
        p_product_id   IN NUMBER
    ) RETURN NUMBER;

    PROCEDURE reserve_stock(
        p_tenant_id    IN NUMBER,
        p_warehouse_id IN NUMBER,
        p_product_id   IN NUMBER,
        p_quantity     IN NUMBER
    );

    PROCEDURE release_stock(
        p_tenant_id    IN NUMBER,
        p_warehouse_id IN NUMBER,
        p_product_id   IN NUMBER,
        p_quantity     IN NUMBER
    );

    PROCEDURE adjust_stock(
        p_tenant_id    IN NUMBER,
        p_warehouse_id IN NUMBER,
        p_product_id   IN NUMBER,
        p_quantity     IN NUMBER
    );
END pkg_inventory;
/

--=====================================================================
-- PKG_ORDER_MANAGEMENT specification
--=====================================================================
CREATE OR REPLACE PACKAGE pkg_order_management AS
    FUNCTION create_order(
        p_tenant_id    IN NUMBER,
        p_customer_id  IN NUMBER,
        p_currency     IN VARCHAR2
    ) RETURN NUMBER;

    PROCEDURE add_order_line(
        p_tenant_id    IN NUMBER,
        p_order_id     IN NUMBER,
        p_product_id   IN NUMBER,
        p_quantity     IN NUMBER,
        p_warehouse_id IN NUMBER
    );

    PROCEDURE confirm_order(
        p_tenant_id    IN NUMBER,
        p_order_id     IN NUMBER,
        p_actor        IN VARCHAR2 DEFAULT 'APP_JAVA',
        p_correlation_id IN VARCHAR2 DEFAULT NULL
    );

    PROCEDURE cancel_order(
        p_tenant_id    IN NUMBER,
        p_order_id     IN NUMBER,
        p_reason       IN VARCHAR2 DEFAULT NULL,
        p_actor        IN VARCHAR2 DEFAULT 'APP_JAVA',
        p_correlation_id IN VARCHAR2 DEFAULT NULL
    );

    PROCEDURE register_payment(
        p_tenant_id       IN NUMBER,
        p_order_id        IN NUMBER,
        p_payment_ref     IN VARCHAR2,
        p_amount          IN NUMBER,
        p_currency        IN VARCHAR2,
        p_payment_method  IN VARCHAR2,
        p_actor           IN VARCHAR2 DEFAULT 'APP_JAVA',
        p_correlation_id  IN VARCHAR2 DEFAULT NULL
    );
END pkg_order_management;
/
</content>