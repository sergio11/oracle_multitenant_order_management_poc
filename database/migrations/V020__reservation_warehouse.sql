--=====================================================================
-- V020 - Persist the reservation warehouse on order lines (M4)
--
-- PROBLEM: confirm_order resolves the "first ACTIVE warehouse for the
-- tenant" at confirm time but never records WHERE the stock was
-- reserved; cancel_order re-resolves the warehouse at cancel time. If
-- the tenant's warehouse set changes between confirm and cancel, the
-- release targets a different inventory row -> ORA-20014, the order
-- can never be cancelled and reserved stock leaks.
--
-- FIX: store the warehouse used at reservation time on each line.
--   * reserved_warehouse_id is NULLABLE on purpose: lines confirmed
--     before this migration (and lines of not-yet-confirmed orders)
--     carry no value - cancel_order falls back to the legacy "first
--     ACTIVE warehouse" resolution for NULL rows (pre-V020 behaviour),
--     so no backfill is required.
--   * FK guards against dangling warehouse references (warehouse rows
--     become effectively immutable while referenced).
--   * Index supports warehouse-scoped lookups / FK maintenance.
--=====================================================================
ALTER TABLE sales_order_line ADD (
    reserved_warehouse_id NUMBER(12)
);

COMMENT ON COLUMN sales_order_line.reserved_warehouse_id IS
    'Warehouse used by confirm_order to reserve stock (NULL until confirmed; NULL also for lines confirmed before V020)';

ALTER TABLE sales_order_line ADD CONSTRAINT fk_line_warehouse
    FOREIGN KEY (reserved_warehouse_id) REFERENCES warehouse (warehouse_id);

CREATE INDEX idx_line_reserved_warehouse ON sales_order_line (reserved_warehouse_id);
