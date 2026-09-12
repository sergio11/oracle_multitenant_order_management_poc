--=====================================================================
-- V009 - Tenant-aware indexes and remaining constraints
--
-- Indexes designed against real access patterns (SRS 44).
-- Most queries are tenant-scoped, so TENANT_ID is the leading column
-- where the access pattern justifies it (SRS 45).
--=====================================================================

--=====================================================================
-- CUSTOMER queries (tenant-scoped)
--=====================================================================
CREATE INDEX idx_customer_tenant_status  ON customer (tenant_id, status);
CREATE INDEX idx_customer_tenant_created ON customer (tenant_id, created_at);
CREATE INDEX idx_addr_tenant_customer    ON customer_address (tenant_id, customer_id);
CREATE INDEX idx_acct_tenant_customer    ON customer_account (tenant_id, customer_id);

--=====================================================================
-- PRODUCT queries (tenant-scoped)
--=====================================================================
-- idx_product_tenant_category already created in V003.
-- uq_product_sku covers (tenant_id, sku).
CREATE INDEX idx_product_tenant_status ON product (tenant_id, status);
CREATE INDEX idx_product_tenant_created ON product (tenant_id, created_at);

--=====================================================================
-- INVENTORY queries (tenant-scoped access)
--=====================================================================
-- uq_inventory_wh_product covers (tenant_id, warehouse_id, product_id).
-- Additional index to find inventory rows by product across warehouses.
CREATE INDEX idx_inventory_tenant_product ON inventory (tenant_id, product_id);

--=====================================================================
-- SALES_ORDER queries (tenant-scoped, SRS 44 examples)
--=====================================================================
-- uq_order_number covers (tenant_id, order_number).
CREATE INDEX idx_order_tenant_status  ON sales_order (tenant_id, status);
CREATE INDEX idx_order_tenant_customer ON sales_order (tenant_id, customer_id);
CREATE INDEX idx_order_tenant_created ON sales_order (tenant_id, created_at);

--=====================================================================
-- SALES_ORDER_LINE queries
--=====================================================================
CREATE INDEX idx_line_tenant_order ON sales_order_line (tenant_id, order_id);
CREATE INDEX idx_line_tenant_product ON sales_order_line (tenant_id, product_id);

--=====================================================================
-- ORDER_STATUS_HISTORY queries
--=====================================================================
CREATE INDEX idx_history_tenant_order ON order_status_history (tenant_id, order_id);
CREATE INDEX idx_history_tenant_time ON order_status_history (tenant_id, changed_at);

--=====================================================================
-- PAYMENT queries
--=====================================================================
CREATE INDEX idx_payment_tenant_order ON payment (tenant_id, order_id);
CREATE INDEX idx_payment_tenant_status ON payment (tenant_id, status);

--=====================================================================
-- AUDIT queries (tenant-scoped by time)
--=====================================================================
-- idx_audit_tenant_time created in V007.
CREATE INDEX idx_audit_tenant_entity ON audit_event (tenant_id, entity_type, entity_id);

--=====================================================================
-- OUTBOX queries
--=====================================================================
-- idx_outbox_tenant_status created in V008.