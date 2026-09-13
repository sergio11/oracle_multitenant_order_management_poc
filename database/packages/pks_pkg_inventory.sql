--=====================================================================
-- PKG_INVENTORY - Package specification
--
-- Inventory operations (SRS 34). RESERVE_STOCK performs atomic,
-- concurrency-safe reservation using SELECT ... FOR UPDATE (SRS 31).
--=====================================================================
CREATE OR REPLACE PACKAGE pkg_inventory AS

    -- Returns available stock for a product in a warehouse (tenant-scoped).
    FUNCTION get_available_stock(
        p_tenant_id    IN NUMBER,
        p_warehouse_id IN NUMBER,
        p_product_id   IN NUMBER
    ) RETURN NUMBER;

    -- Atomically reserves stock. Raises INSUFFICIENT_INVENTORY if not enough.
    PROCEDURE reserve_stock(
        p_tenant_id    IN NUMBER,
        p_warehouse_id IN NUMBER,
        p_product_id   IN NUMBER,
        p_quantity     IN NUMBER
    );

    -- Releases previously reserved stock (e.g. order cancellation).
    PROCEDURE release_stock(
        p_tenant_id    IN NUMBER,
        p_warehouse_id IN NUMBER,
        p_product_id   IN NUMBER,
        p_quantity     IN NUMBER
    );

    -- Adjusts available stock (manual correction / restock).
    PROCEDURE adjust_stock(
        p_tenant_id    IN NUMBER,
        p_warehouse_id IN NUMBER,
        p_product_id   IN NUMBER,
        p_quantity     IN NUMBER
    );

    -- Test helper: set stock to a specific quantity (concurrency tests)
    PROCEDURE set_test_stock(
        p_tenant_id    IN NUMBER,
        p_product_id   IN NUMBER,
        p_warehouse_id IN NUMBER,
        p_qty          IN NUMBER
    );

END pkg_inventory;
/