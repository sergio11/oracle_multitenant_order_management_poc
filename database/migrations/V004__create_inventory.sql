--=====================================================================
-- V004 - WAREHOUSE, INVENTORY
--
-- Inventory domain (SRS 21, 22).
-- Tenant-aware tables. INVENTORY uses VERSION for optimistic locking
-- and is the contention point for concurrent order confirmation.
--=====================================================================

--=====================================================================
-- WAREHOUSE (SRS 21)
--=====================================================================
CREATE TABLE warehouse (
    warehouse_id    NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id       NUMBER          NOT NULL,
    code            VARCHAR2(30)    NOT NULL,
    name            VARCHAR2(200)   NOT NULL,
    location        VARCHAR2(200),
    status          VARCHAR2(20)    NOT NULL,
    created_at      TIMESTAMP(6)    DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at      TIMESTAMP(6)    DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT fk_warehouse_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(tenant_id),
    CONSTRAINT ck_warehouse_status CHECK (status IN ('ACTIVE','INACTIVE','MAINTENANCE')),
    CONSTRAINT uq_warehouse_code UNIQUE (tenant_id, code)
);

COMMENT ON TABLE warehouse IS 'Warehouse belonging to one tenant (SRS 21)';
COMMENT ON COLUMN warehouse.tenant_id IS 'Owner tenant (multi-tenancy)';

--=====================================================================
-- INVENTORY (SRS 22)
--
-- Unique on (TENANT_ID, WAREHOUSE_ID, PRODUCT_ID).
-- VERSION column supports optimistic locking during reservation.
--=====================================================================
CREATE TABLE inventory (
    inventory_id       NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id          NUMBER          NOT NULL,
    warehouse_id       NUMBER          NOT NULL,
    product_id         NUMBER          NOT NULL,
    available_quantity NUMBER(12,0)    NOT NULL,
    reserved_quantity  NUMBER(12,0)    NOT NULL,
    version            NUMBER          DEFAULT 0 NOT NULL,
    updated_at         TIMESTAMP(6)    DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT fk_inventory_tenant    FOREIGN KEY (tenant_id)    REFERENCES tenant(tenant_id),
    CONSTRAINT fk_inventory_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouse(warehouse_id),
    CONSTRAINT fk_inventory_product   FOREIGN KEY (product_id)   REFERENCES product(product_id),
    CONSTRAINT ck_inventory_available CHECK (available_quantity >= 0),
    CONSTRAINT ck_inventory_reserved  CHECK (reserved_quantity  >= 0),
    CONSTRAINT uq_inventory_wh_product UNIQUE (tenant_id, warehouse_id, product_id)
);

COMMENT ON TABLE inventory IS 'Stock per warehouse/product (SRS 22)';
COMMENT ON COLUMN inventory.tenant_id IS 'Owner tenant (multi-tenancy)';
COMMENT ON COLUMN inventory.version IS 'Optimistic locking version (SRS 31)';
COMMENT ON COLUMN inventory.available_quantity IS 'Must never go below zero - overselling guard';
COMMENT ON COLUMN inventory.reserved_quantity IS 'Quantity reserved by confirmed orders';