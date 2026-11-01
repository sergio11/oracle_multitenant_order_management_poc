--=====================================================================
-- PKG_INVENTORY - Package body
--
-- Implements inventory operations (SRS 34).
-- RESERVE_STOCK uses SELECT ... FOR UPDATE to lock the inventory row,
-- guaranteeing no overselling under concurrency (SRS 31, FT-003).
-- All check constraints on INVENTORY prevent negative quantities.
--=====================================================================
CREATE OR REPLACE PACKAGE BODY pkg_inventory AS

    --=================================================================
    -- get_available_stock
    --=================================================================
    FUNCTION get_available_stock(
        p_tenant_id    IN NUMBER,
        p_warehouse_id IN NUMBER,
        p_product_id   IN NUMBER
    ) RETURN NUMBER IS
        v_qty NUMBER;
    BEGIN
        SELECT available_quantity
          INTO v_qty
          FROM inventory
         WHERE tenant_id    = p_tenant_id
           AND warehouse_id = p_warehouse_id
           AND product_id   = p_product_id;

        RETURN v_qty;

    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            RETURN 0;
    END get_available_stock;

    --=================================================================
    -- reserve_stock
    -- Atomic reservation with row lock (SELECT ... FOR UPDATE).
    --=================================================================
    PROCEDURE reserve_stock(
        p_tenant_id    IN NUMBER,
        p_warehouse_id IN NUMBER,
        p_product_id   IN NUMBER,
        p_quantity     IN NUMBER
    ) IS
        v_available NUMBER;
        v_inventory_id NUMBER;
    BEGIN
        IF p_quantity <= 0 THEN
            RAISE_APPLICATION_ERROR(-20010, 'Reservation quantity must be positive.');
        END IF;

        -- Lock the inventory row for this tenant/warehouse/product.
        -- WAIT 5: contention must surface as an actionable lock timeout
        -- (ORA-30006 -> 409) instead of blocking the request thread and
        -- the connection pool indefinitely. Ordering by product_id in the
        -- caller keeps the lock order deterministic and deadlocks rare.
        SELECT inventory_id, available_quantity
          INTO v_inventory_id, v_available
          FROM inventory
         WHERE tenant_id    = p_tenant_id
           AND warehouse_id = p_warehouse_id
           AND product_id   = p_product_id
           FOR UPDATE WAIT 5;

        -- Guard against overselling (SRS 31, FR-010).
        IF v_available < p_quantity THEN
            RAISE_APPLICATION_ERROR(-20011, 'INSUFFICIENT_INVENTORY for product ' || p_product_id);
        END IF;

        -- Atomic decrement/increment + optimistic version bump.
        UPDATE inventory
           SET available_quantity = available_quantity - p_quantity,
               reserved_quantity  = reserved_quantity  + p_quantity,
               version            = version + 1,
               updated_at         = SYSTIMESTAMP
         WHERE inventory_id = v_inventory_id;

    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            RAISE_APPLICATION_ERROR(-20012, 'Inventory row not found for product ' || p_product_id);
    END reserve_stock;

    --=================================================================
    -- release_stock
    --=================================================================
    PROCEDURE release_stock(
        p_tenant_id    IN NUMBER,
        p_warehouse_id IN NUMBER,
        p_product_id   IN NUMBER,
        p_quantity     IN NUMBER
    ) IS
        v_reserved NUMBER;
        v_inventory_id NUMBER;
    BEGIN
        IF p_quantity <= 0 THEN
            RAISE_APPLICATION_ERROR(-20013, 'Release quantity must be positive.');
        END IF;

        SELECT inventory_id, reserved_quantity
          INTO v_inventory_id, v_reserved
          FROM inventory
         WHERE tenant_id    = p_tenant_id
           AND warehouse_id = p_warehouse_id
           AND product_id   = p_product_id
           FOR UPDATE WAIT 5;

        IF v_reserved < p_quantity THEN
            RAISE_APPLICATION_ERROR(-20014, 'Reserved quantity insufficient for release.');
        END IF;

        UPDATE inventory
           SET available_quantity = available_quantity + p_quantity,
               reserved_quantity  = reserved_quantity  - p_quantity,
               version            = version + 1,
               updated_at         = SYSTIMESTAMP
         WHERE inventory_id = v_inventory_id;

    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            RAISE_APPLICATION_ERROR(-20012, 'Inventory row not found for product ' || p_product_id);
    END release_stock;

    --=================================================================
    -- adjust_stock
    --=================================================================
    PROCEDURE adjust_stock(
        p_tenant_id    IN NUMBER,
        p_warehouse_id IN NUMBER,
        p_product_id   IN NUMBER,
        p_quantity     IN NUMBER
    ) IS
    BEGIN
        UPDATE inventory
           SET available_quantity = p_quantity,
               version            = version + 1,
               updated_at         = SYSTIMESTAMP
         WHERE tenant_id    = p_tenant_id
           AND warehouse_id = p_warehouse_id
           AND product_id   = p_product_id;

        IF SQL%ROWCOUNT = 0 THEN
            RAISE_APPLICATION_ERROR(-20012, 'Inventory row not found for product ' || p_product_id);
        END IF;
    END adjust_stock;

    --=================================================================
    -- set_test_stock: test helper for concurrency tests
    --=================================================================
    PROCEDURE set_test_stock(
        p_tenant_id    IN NUMBER,
        p_product_id   IN NUMBER,
        p_warehouse_id IN NUMBER,
        p_qty          IN NUMBER
    ) IS
    BEGIN
        UPDATE inventory
           SET available_quantity = p_qty,
               reserved_quantity  = 0,
               version           = version + 1
         WHERE tenant_id    = p_tenant_id
           AND product_id   = p_product_id
           AND warehouse_id = p_warehouse_id;

        IF SQL%ROWCOUNT = 0 THEN
            RAISE_APPLICATION_ERROR(-20030, 'Inventory record not found for test stock update');
        END IF;
    END set_test_stock;

END pkg_inventory;
/

-- NOTE (closure fix, V023): the former `GRANT EXECUTE ON pkg_inventory
-- TO app_runtime` is removed. app_runtime never calls this package
-- directly (all production stock movement is internal to the
-- definer-rights pkg_order_management calls); the only direct caller
-- was the concurrency test setup, which now runs as APP_OWNER. Keeping
-- the grant would re-expose the set_test_stock backdoor to runtime
-- sessions on every package deploy.