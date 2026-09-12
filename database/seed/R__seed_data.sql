--=====================================================================
-- R__SEED_DATA - Repeatable seed for demo tenants (SRS 91)
--
-- Creates demo tenants ACME, GLOBEX, INITECH plus customers, products,
-- warehouses, inventory and a couple of orders for demo flow (SRS 121).
--
-- Idempotent: uses MERGE / NOT EXISTS so it can run repeatedly and on
-- brand-new or partially-seeded environments.
--=====================================================================

--=====================================================================
-- TENANTS
--=====================================================================
MERGE INTO tenant t
USING (SELECT 'ACME'    AS code, 'Acme Corporation'  AS name, 'STANDARD'  AS tier FROM dual
       UNION ALL SELECT 'GLOBEX',  'Globex Inc',  'PREMIUM'    AS tier FROM dual
       UNION ALL SELECT 'INITECH', 'Initech Ltd', 'ENTERPRISE' AS tier FROM dual) s
ON (t.tenant_code = s.code)
WHEN NOT MATCHED THEN
    INSERT (tenant_code, name, tier, status, isolation_mode)
    VALUES (s.code, s.name, s.tier, 'ACTIVE', 'SHARED_SCHEMA');

--=====================================================================
-- CUSTOMERS per tenant
--=====================================================================
-- ACME customers
MERGE INTO customer c
USING (SELECT t.tenant_id, 'C0001' AS num, 'Wile' AS fn, 'Coyote' AS ln, 'wile@acme.com' AS email
       FROM tenant t WHERE t.tenant_code = 'ACME') s
ON (c.tenant_id = s.tenant_id AND c.customer_number = s.num)
WHEN NOT MATCHED THEN
    INSERT (tenant_id, customer_number, first_name, last_name, email, status)
    VALUES (s.tenant_id, s.num, s.fn, s.ln, s.email, 'ACTIVE');

MERGE INTO customer c
USING (SELECT t.tenant_id, 'C0002' AS num, 'Marvin' AS fn, 'Martin' AS ln, 'marvin@acme.com' AS email
       FROM tenant t WHERE t.tenant_code = 'ACME') s
ON (c.tenant_id = s.tenant_id AND c.customer_number = s.num)
WHEN NOT MATCHED THEN
    INSERT (tenant_id, customer_number, first_name, last_name, email, status)
    VALUES (s.tenant_id, s.num, s.fn, s.ln, s.email, 'ACTIVE');

-- GLOBEX customers
MERGE INTO customer c
USING (SELECT t.tenant_id, 'C0001' AS num, 'Hank' AS fn, 'Scorpio' AS ln, 'hank@globex.com' AS email
       FROM tenant t WHERE t.tenant_code = 'GLOBEX') s
ON (c.tenant_id = s.tenant_id AND c.customer_number = s.num)
WHEN NOT MATCHED THEN
    INSERT (tenant_id, customer_number, first_name, last_name, email, status)
    VALUES (s.tenant_id, s.num, s.fn, s.ln, s.email, 'ACTIVE');

-- INITECH customers
MERGE INTO customer c
USING (SELECT t.tenant_id, 'C0001' AS num, 'Milton' AS fn, 'Waddams' AS ln, 'milton@initech.com' AS email
       FROM tenant t WHERE t.tenant_code = 'INITECH') s
ON (c.tenant_id = s.tenant_id AND c.customer_number = s.num)
WHEN NOT MATCHED THEN
    INSERT (tenant_id, customer_number, first_name, last_name, email, status)
    VALUES (s.tenant_id, s.num, s.fn, s.ln, s.email, 'ACTIVE');

--=====================================================================
-- PRODUCT_CATEGORIES per tenant
--=====================================================================
MERGE INTO product_category pc
USING (SELECT t.tenant_id, 'GENERAL' AS name FROM tenant t WHERE t.tenant_code = 'ACME') s
ON (pc.tenant_id = s.tenant_id AND pc.name = s.name)
WHEN NOT MATCHED THEN
    INSERT (tenant_id, name, status)
    VALUES (s.tenant_id, s.name, 'ACTIVE');

MERGE INTO product_category pc
USING (SELECT t.tenant_id, 'GENERAL' AS name FROM tenant t WHERE t.tenant_code = 'GLOBEX') s
ON (pc.tenant_id = s.tenant_id AND pc.name = s.name)
WHEN NOT MATCHED THEN
    INSERT (tenant_id, name, status)
    VALUES (s.tenant_id, s.name, 'ACTIVE');

MERGE INTO product_category pc
USING (SELECT t.tenant_id, 'GENERAL' AS name FROM tenant t WHERE t.tenant_code = 'INITECH') s
ON (pc.tenant_id = s.tenant_id AND pc.name = s.name)
WHEN NOT MATCHED THEN
    INSERT (tenant_id, name, status)
    VALUES (s.tenant_id, s.name, 'ACTIVE');

--=====================================================================
-- WAREHOUSES per tenant
--=====================================================================
MERGE INTO warehouse w
USING (SELECT t.tenant_id, 'WH1' AS code, 'Main Warehouse' AS name
       FROM tenant t WHERE t.tenant_code = 'ACME') s
ON (w.tenant_id = s.tenant_id AND w.code = s.code)
WHEN NOT MATCHED THEN
    INSERT (tenant_id, code, name, location, status)
    VALUES (s.tenant_id, s.code, s.name, 'Springfield', 'ACTIVE');

MERGE INTO warehouse w
USING (SELECT t.tenant_id, 'WH1' AS code, 'Main Warehouse' AS name
       FROM tenant t WHERE t.tenant_code = 'GLOBEX') s
ON (w.tenant_id = s.tenant_id AND w.code = s.code)
WHEN NOT MATCHED THEN
    INSERT (tenant_id, code, name, location, status)
    VALUES (s.tenant_id, s.code, s.name, 'Cypress Creek', 'ACTIVE');

MERGE INTO warehouse w
USING (SELECT t.tenant_id, 'WH1' AS code, 'Main Warehouse' AS name
       FROM tenant t WHERE t.tenant_code = 'INITECH') s
ON (w.tenant_id = s.tenant_id AND w.code = s.code)
WHEN NOT MATCHED THEN
    INSERT (tenant_id, code, name, location, status)
    VALUES (s.tenant_id, s.code, s.name, 'New Orleans', 'ACTIVE');

--=====================================================================
-- PRODUCTS per tenant
--=====================================================================
MERGE INTO product p
USING (SELECT t.tenant_id, 'PRD-001' AS sku, 'Widget A' AS name, 25.00 AS price
       FROM tenant t WHERE t.tenant_code = 'ACME') s
ON (p.tenant_id = s.tenant_id AND p.sku = s.sku)
WHEN NOT MATCHED THEN
    INSERT (tenant_id, sku, name, unit_price, currency, status)
    VALUES (s.tenant_id, s.sku, s.name, s.price, 'USD', 'ACTIVE');

MERGE INTO product p
USING (SELECT t.tenant_id, 'PRD-002' AS sku, 'Gadget B' AS name, 49.99 AS price
       FROM tenant t WHERE t.tenant_code = 'ACME') s
ON (p.tenant_id = s.tenant_id AND p.sku = s.sku)
WHEN NOT MATCHED THEN
    INSERT (tenant_id, sku, name, unit_price, currency, status)
    VALUES (s.tenant_id, s.sku, s.name, s.price, 'USD', 'ACTIVE');

MERGE INTO product p
USING (SELECT t.tenant_id, 'PRD-001' AS sku, 'Doomsday Device' AS name, 99.00 AS price
       FROM tenant t WHERE t.tenant_code = 'GLOBEX') s
ON (p.tenant_id = s.tenant_id AND p.sku = s.sku)
WHEN NOT MATCHED THEN
    INSERT (tenant_id, sku, name, unit_price, currency, status)
    VALUES (s.tenant_id, s.sku, s.name, s.price, 'USD', 'ACTIVE');

MERGE INTO product p
USING (SELECT t.tenant_id, 'PRD-001' AS sku, 'PC Load Letter' AS name, 1200.00 AS price
       FROM tenant t WHERE t.tenant_code = 'INITECH') s
ON (p.tenant_id = s.tenant_id AND p.sku = s.sku)
WHEN NOT MATCHED THEN
    INSERT (tenant_id, sku, name, unit_price, currency, status)
    VALUES (s.tenant_id, s.sku, s.name, s.price, 'USD', 'ACTIVE');

--=====================================================================
-- INVENTORY per tenant (enough stock for demo + concurrency tests)
--=====================================================================
MERGE INTO inventory i
USING (
    SELECT p.tenant_id, w.warehouse_id, p.product_id, 100 AS qty
    FROM product p
    JOIN warehouse w ON w.tenant_id = p.tenant_id
    WHERE p.sku = 'PRD-001'
) s
ON (i.tenant_id = s.tenant_id AND i.warehouse_id = s.warehouse_id AND i.product_id = s.product_id)
WHEN NOT MATCHED THEN
    INSERT (tenant_id, warehouse_id, product_id, available_quantity, reserved_quantity)
    VALUES (s.tenant_id, s.warehouse_id, s.product_id, s.qty, 0);

--=====================================================================
-- Demo orders (ACME as demo flow, SRS 121)
--=====================================================================
-- Create one PENDING order for ACME so the demo can confirm it.
MERGE INTO sales_order o
USING (
    SELECT c.tenant_id, 'SO-ACME-0001' AS num, c.customer_id
    FROM customer c
    JOIN tenant t ON t.tenant_id = c.tenant_id
    WHERE t.tenant_code = 'ACME' AND c.customer_number = 'C0001'
) s
ON (o.tenant_id = s.tenant_id AND o.order_number = s.num)
WHEN NOT MATCHED THEN
    INSERT (tenant_id, order_number, customer_id, status, currency, total_amount)
    VALUES (s.tenant_id, s.num, s.customer_id, 'PENDING', 'USD', 50.00);

-- Add a line to the demo order and keep total consistent.
MERGE INTO sales_order_line l
USING (
    SELECT o.tenant_id, o.order_id, p.product_id
    FROM sales_order o
    JOIN tenant t ON t.tenant_id = o.tenant_id
    JOIN product p ON p.tenant_id = o.tenant_id
    WHERE t.tenant_code = 'ACME' AND o.order_number = 'SO-ACME-0001' AND p.sku = 'PRD-001'
) s
ON (l.tenant_id = s.tenant_id AND l.order_id = s.order_id AND l.product_id = s.product_id)
WHEN NOT MATCHED THEN
    INSERT (tenant_id, order_id, product_id, quantity, unit_price, line_total)
    VALUES (s.tenant_id, s.order_id, s.product_id, 2, 25.00, 50.00);

--=====================================================================
-- End of seed data
--=====================================================================