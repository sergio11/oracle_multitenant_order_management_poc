--=====================================================================
-- V005 - SALES_ORDER, SALES_ORDER_LINE, ORDER_STATUS_HISTORY
--
-- Order domain (SRS 23, 24, 25).
-- Tenant-aware tables with full order lifecycle tracking.
--=====================================================================

--=====================================================================
-- SALES_ORDER (SRS 23)
--
-- States: DRAFT PENDING CONFIRMED ALLOCATED PAID SHIPPED COMPLETED CANCELLED
-- VERSION used for optimistic locking throughout order lifecycle.
--=====================================================================
CREATE TABLE sales_order (
    order_id        NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id       NUMBER          NOT NULL,
    order_number    VARCHAR2(30)    NOT NULL,
    customer_id     NUMBER          NOT NULL,
    status          VARCHAR2(20)    NOT NULL,
    currency        VARCHAR2(3)     NOT NULL,
    total_amount    NUMBER(18,2)    NOT NULL,
    created_at      TIMESTAMP(6)    DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at      TIMESTAMP(6)    DEFAULT SYSTIMESTAMP NOT NULL,
    version         NUMBER          DEFAULT 0 NOT NULL,
    CONSTRAINT fk_order_tenant   FOREIGN KEY (tenant_id)   REFERENCES tenant(tenant_id),
    CONSTRAINT fk_order_customer FOREIGN KEY (customer_id) REFERENCES customer(customer_id),
    CONSTRAINT ck_order_status CHECK (status IN ('DRAFT','PENDING','CONFIRMED','ALLOCATED',
                                                'PAID','SHIPPED','COMPLETED','CANCELLED')),
    CONSTRAINT ck_order_currency CHECK (LENGTH(currency) = 3),
    CONSTRAINT uq_order_number UNIQUE (tenant_id, order_number)
);

COMMENT ON TABLE sales_order IS 'Sales order per tenant (SRS 23)';
COMMENT ON COLUMN sales_order.tenant_id IS 'Owner tenant (multi-tenancy)';
COMMENT ON COLUMN sales_order.status IS 'Order lifecycle state (SRS 23)';
COMMENT ON COLUMN sales_order.version IS 'Optimistic locking version';

--=====================================================================
-- SALES_ORDER_LINE (SRS 24)
--=====================================================================
CREATE TABLE sales_order_line (
    order_line_id   NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id       NUMBER          NOT NULL,
    order_id        NUMBER          NOT NULL,
    product_id      NUMBER          NOT NULL,
    quantity        NUMBER(12,0)    NOT NULL,
    unit_price      NUMBER(18,2)    NOT NULL,
    line_total      NUMBER(18,2)    NOT NULL,
    created_at      TIMESTAMP(6)    DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT fk_line_tenant   FOREIGN KEY (tenant_id)   REFERENCES tenant(tenant_id),
    CONSTRAINT fk_line_order    FOREIGN KEY (order_id)    REFERENCES sales_order(order_id),
    CONSTRAINT fk_line_product  FOREIGN KEY (product_id)  REFERENCES product(product_id),
    CONSTRAINT ck_line_quantity CHECK (quantity > 0),
    CONSTRAINT ck_line_total CHECK (line_total >= 0)
);

COMMENT ON TABLE sales_order_line IS 'Order line items (SRS 24)';
COMMENT ON COLUMN sales_order_line.tenant_id IS 'Owner tenant (multi-tenancy)';

--=====================================================================
-- ORDER_STATUS_HISTORY (SRS 25)
--
-- Every important state transition is recorded.
--=====================================================================
CREATE TABLE order_status_history (
    status_history_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id         NUMBER          NOT NULL,
    order_id          NUMBER          NOT NULL,
    old_status        VARCHAR2(20),
    new_status        VARCHAR2(20)    NOT NULL,
    changed_by        VARCHAR2(100)   NOT NULL,
    changed_at        TIMESTAMP(6)    DEFAULT SYSTIMESTAMP NOT NULL,
    reason            VARCHAR2(500),
    CONSTRAINT fk_history_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(tenant_id),
    CONSTRAINT fk_history_order  FOREIGN KEY (order_id)  REFERENCES sales_order(order_id)
);

COMMENT ON TABLE order_status_history IS 'Order state change audit trail (SRS 25)';
COMMENT ON COLUMN order_status_history.tenant_id IS 'Owner tenant (multi-tenancy)';