--=====================================================================
-- PKG_ORDER_MANAGEMENT - Package specification
--
-- Order lifecycle (SRS 33). This package orchestrates the full order
-- transaction: create, add line, confirm (with inventory reservation),
-- cancel and register payment. Each operation is atomic (SRS 37, 61).
--=====================================================================
CREATE OR REPLACE PACKAGE pkg_order_management AS

    -- Creates a sales order and returns its ID.
    FUNCTION create_order(
        p_tenant_id    IN NUMBER,
        p_customer_id  IN NUMBER,
        p_currency     IN VARCHAR2
    ) RETURN NUMBER;

    -- Adds a line to an order (recalculates total).
    PROCEDURE add_order_line(
        p_tenant_id    IN NUMBER,
        p_order_id     IN NUMBER,
        p_product_id   IN NUMBER,
        p_quantity     IN NUMBER,
        p_warehouse_id IN NUMBER
    );

    -- Confirms an order: validates, locks & reserves inventory,
    -- transitions status and writes audit/outbox in ONE transaction.
    PROCEDURE confirm_order(
        p_tenant_id    IN NUMBER,
        p_order_id     IN NUMBER,
        p_actor        IN VARCHAR2 DEFAULT 'APP_JAVA',
        p_correlation_id IN VARCHAR2 DEFAULT NULL
    );

    -- Cancels an order (releases reserved inventory if confirmed).
    PROCEDURE cancel_order(
        p_tenant_id    IN NUMBER,
        p_order_id     IN NUMBER,
        p_reason       IN VARCHAR2 DEFAULT NULL,
        p_actor        IN VARCHAR2 DEFAULT 'APP_JAVA',
        p_correlation_id IN VARCHAR2 DEFAULT NULL
    );

    -- Registers a payment against an order.
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