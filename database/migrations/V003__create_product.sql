--=====================================================================
-- V003 - PRODUCT_CATEGORY, PRODUCT
--
-- Product domain (SRS 19, 20).
-- Tenant-aware tables (TENANT_ID present).
--=====================================================================

--=====================================================================
-- PRODUCT_CATEGORY (SRS 19)
--=====================================================================
CREATE TABLE product_category (
    category_id     NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id       NUMBER          NOT NULL,
    name            VARCHAR2(100)   NOT NULL,
    description     VARCHAR2(500),
    status          VARCHAR2(20)    NOT NULL,
    created_at      TIMESTAMP(6)    DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at      TIMESTAMP(6)    DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT fk_category_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(tenant_id),
    CONSTRAINT ck_category_status CHECK (status IN ('ACTIVE','INACTIVE')),
    CONSTRAINT uq_category_tenant_name UNIQUE (tenant_id, name)
);

COMMENT ON TABLE product_category IS 'Product categories (SRS 19)';
COMMENT ON COLUMN product_category.tenant_id IS 'Owner tenant (multi-tenancy)';

--=====================================================================
-- PRODUCT (SRS 20)
--=====================================================================
CREATE TABLE product (
    product_id      NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id       NUMBER          NOT NULL,
    sku             VARCHAR2(50)    NOT NULL,
    name            VARCHAR2(200)   NOT NULL,
    description     VARCHAR2(500),
    category_id     NUMBER,
    unit_price      NUMBER(18,2)    NOT NULL,
    currency        VARCHAR2(3)     NOT NULL,
    status          VARCHAR2(20)    NOT NULL,
    created_at      TIMESTAMP(6)    DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at      TIMESTAMP(6)    DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT fk_product_tenant   FOREIGN KEY (tenant_id)   REFERENCES tenant(tenant_id),
    CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES product_category(category_id),
    CONSTRAINT ck_product_status CHECK (status IN ('ACTIVE','INACTIVE','DISCONTINUED')),
    CONSTRAINT ck_product_currency CHECK (LENGTH(currency) = 3),
    CONSTRAINT uq_product_sku UNIQUE (tenant_id, sku)
);

COMMENT ON TABLE product IS 'Product belonging to one tenant (SRS 20)';
COMMENT ON COLUMN product.tenant_id IS 'Owner tenant (multi-tenancy)';
COMMENT ON COLUMN product.sku IS 'Stock keeping unit, unique per tenant';

-- Index for tenant-scoped category lookups (SRS 45)
CREATE INDEX idx_product_tenant_category ON product (tenant_id, category_id);