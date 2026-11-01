--=====================================================================
-- R__SEED_DATA - Repeatable seed for demo tenants (SRS 91)
--
-- Creates demo tenants ACME, GLOBEX, INITECH plus customers, products,
-- warehouses, inventory and demo orders (SRS 121). Every product of
-- each tenant gets stock (needed by demo + concurrency tests).
--
-- Idempotent: uses MERGE / NOT EXISTS so it can run repeatedly and on
-- brand-new or partially-seeded environments.
--
-- VPD: since V011, tenant-aware tables carry DBMS_RLS policies with
-- update_check=TRUE. The seed runs as APP_OWNER WITHOUT a tenant
-- context, which would fail with ORA-28104 on INSERT/UPDATE.
-- Each tenant section therefore sets APP_CTX.TENANT_ID via
-- pkg_tenant.set_tenant_context (the sanctioned path, same as the
-- backend). Direct DBMS_SESSION.SET_CONTEXT from an anonymous block
-- is NOT allowed: Oracle only permits SET_CONTEXT on a USING-clause
-- namespace from within the designated package (ORA-01031 otherwise).
-- Prerequisite: package bodies must be deployed first
-- (database/packages/deploy_packages.sql) - the rake qa:db:init flow
-- runs packages before seed for exactly this reason.
--=====================================================================

--=====================================================================
-- TENANTS (no VPD policy on TENANT - safe without context)
--
-- The tenancy rows MUST get identical tenant_id values in every
-- provisioned PDB (the shared PDB resolves the tenant, the dedicated
-- PDB consumes that same id as the VPD/FK reference). Oracle is free to
-- order a UNION ALL subquery arbitrarily, so the seed pins the order:
-- with a stable order the identity sequence assigns the same ids in
-- PDB_OLTP and PDB_CORP.
--=====================================================================
MERGE INTO tenant t
USING (SELECT code, name, tier
       FROM (SELECT 'ACME'    AS code, 'Acme Corporation'  AS name, 'STANDARD'  AS tier FROM dual
             UNION ALL SELECT 'GLOBEX',  'Globex Inc',  'PREMIUM'    AS tier FROM dual
             UNION ALL SELECT 'INITECH', 'Initech Ltd', 'ENTERPRISE' AS tier FROM dual)
       ORDER BY code) s
ON (t.tenant_code = s.code)
WHEN NOT MATCHED THEN
    INSERT (tenant_code, name, tier, status, isolation_mode)
    VALUES (s.code, s.name, s.tier, 'ACTIVE',
            CASE WHEN s.tier = 'ENTERPRISE' THEN 'DEDICATED_PDB'
                 ELSE 'SHARED_SCHEMA' END);

--=====================================================================
-- ACME data
--=====================================================================
DECLARE
    v_tenant_id NUMBER;
BEGIN
    SELECT tenant_id INTO v_tenant_id FROM tenant WHERE tenant_code = 'ACME';
    pkg_tenant.set_tenant_context(v_tenant_id);
END;
/

-- Customers
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

-- Product category
MERGE INTO product_category pc
USING (SELECT t.tenant_id, 'GENERAL' AS name FROM tenant t WHERE t.tenant_code = 'ACME') s
ON (pc.tenant_id = s.tenant_id AND pc.name = s.name)
WHEN NOT MATCHED THEN
    INSERT (tenant_id, name, status)
    VALUES (s.tenant_id, s.name, 'ACTIVE');

-- Warehouse
MERGE INTO warehouse w
USING (SELECT t.tenant_id, 'WH1' AS code, 'Main Warehouse' AS name
       FROM tenant t WHERE t.tenant_code = 'ACME') s
ON (w.tenant_id = s.tenant_id AND w.code = s.code)
WHEN NOT MATCHED THEN
    INSERT (tenant_id, code, name, location, status)
    VALUES (s.tenant_id, s.code, s.name, 'Springfield', 'ACTIVE');

-- Products
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

-- Inventory: EVERY product of the tenant (no SKU filter)
MERGE INTO inventory i
USING (
    SELECT p.tenant_id, w.warehouse_id, p.product_id, 100 AS qty
    FROM product p
    JOIN warehouse w ON w.tenant_id = p.tenant_id
    WHERE p.tenant_id = (SELECT tenant_id FROM tenant WHERE tenant_code = 'ACME')
) s
ON (i.tenant_id = s.tenant_id AND i.warehouse_id = s.warehouse_id AND i.product_id = s.product_id)
WHEN NOT MATCHED THEN
    INSERT (tenant_id, warehouse_id, product_id, available_quantity, reserved_quantity)
    VALUES (s.tenant_id, s.warehouse_id, s.product_id, s.qty, 0);

-- Demo order 1 (ACME, single line) so the demo can confirm it.
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

-- Demo order 2 (ACME, MULTI-LINE: two lines) - exercises multi-line orders.
MERGE INTO sales_order o
USING (
    SELECT c.tenant_id, 'SO-ACME-0002' AS num, c.customer_id
    FROM customer c
    JOIN tenant t ON t.tenant_id = c.tenant_id
    WHERE t.tenant_code = 'ACME' AND c.customer_number = 'C0002'
) s
ON (o.tenant_id = s.tenant_id AND o.order_number = s.num)
WHEN NOT MATCHED THEN
    INSERT (tenant_id, order_number, customer_id, status, currency, total_amount)
    VALUES (s.tenant_id, s.num, s.customer_id, 'PENDING', 'USD', 74.99);

MERGE INTO sales_order_line l
USING (
    SELECT o.tenant_id, o.order_id, p.product_id, p.unit_price, p.sku
    FROM sales_order o
    JOIN tenant t ON t.tenant_id = o.tenant_id
    JOIN product p ON p.tenant_id = o.tenant_id
    WHERE t.tenant_code = 'ACME' AND o.order_number = 'SO-ACME-0002'
      AND p.sku IN ('PRD-001','PRD-002')
) s
ON (l.tenant_id = s.tenant_id AND l.order_id = s.order_id AND l.product_id = s.product_id)
WHEN NOT MATCHED THEN
    INSERT (tenant_id, order_id, product_id, quantity, unit_price, line_total)
    VALUES (s.tenant_id, s.order_id, s.product_id, 1, s.unit_price, s.unit_price);

BEGIN
    pkg_tenant.clear_tenant_context;
END;
/

--=====================================================================
-- GLOBEX data
--=====================================================================
DECLARE
    v_tenant_id NUMBER;
BEGIN
    SELECT tenant_id INTO v_tenant_id FROM tenant WHERE tenant_code = 'GLOBEX';
    pkg_tenant.set_tenant_context(v_tenant_id);
END;
/

MERGE INTO customer c
USING (SELECT t.tenant_id, 'C0001' AS num, 'Hank' AS fn, 'Scorpio' AS ln, 'hank@globex.com' AS email
       FROM tenant t WHERE t.tenant_code = 'GLOBEX') s
ON (c.tenant_id = s.tenant_id AND c.customer_number = s.num)
WHEN NOT MATCHED THEN
    INSERT (tenant_id, customer_number, first_name, last_name, email, status)
    VALUES (s.tenant_id, s.num, s.fn, s.ln, s.email, 'ACTIVE');

MERGE INTO product_category pc
USING (SELECT t.tenant_id, 'GENERAL' AS name FROM tenant t WHERE t.tenant_code = 'GLOBEX') s
ON (pc.tenant_id = s.tenant_id AND pc.name = s.name)
WHEN NOT MATCHED THEN
    INSERT (tenant_id, name, status)
    VALUES (s.tenant_id, s.name, 'ACTIVE');

MERGE INTO warehouse w
USING (SELECT t.tenant_id, 'WH1' AS code, 'Main Warehouse' AS name
       FROM tenant t WHERE t.tenant_code = 'GLOBEX') s
ON (w.tenant_id = s.tenant_id AND w.code = s.code)
WHEN NOT MATCHED THEN
    INSERT (tenant_id, code, name, location, status)
    VALUES (s.tenant_id, s.code, s.name, 'Cypress Creek', 'ACTIVE');

MERGE INTO product p
USING (SELECT t.tenant_id, 'PRD-001' AS sku, 'Doomsday Device' AS name, 99.00 AS price
       FROM tenant t WHERE t.tenant_code = 'GLOBEX') s
ON (p.tenant_id = s.tenant_id AND p.sku = s.sku)
WHEN NOT MATCHED THEN
    INSERT (tenant_id, sku, name, unit_price, currency, status)
    VALUES (s.tenant_id, s.sku, s.name, s.price, 'USD', 'ACTIVE');

-- Inventory: EVERY product of the tenant
MERGE INTO inventory i
USING (
    SELECT p.tenant_id, w.warehouse_id, p.product_id, 100 AS qty
    FROM product p
    JOIN warehouse w ON w.tenant_id = p.tenant_id
    WHERE p.tenant_id = (SELECT tenant_id FROM tenant WHERE tenant_code = 'GLOBEX')
) s
ON (i.tenant_id = s.tenant_id AND i.warehouse_id = s.warehouse_id AND i.product_id = s.product_id)
WHEN NOT MATCHED THEN
    INSERT (tenant_id, warehouse_id, product_id, available_quantity, reserved_quantity)
    VALUES (s.tenant_id, s.warehouse_id, s.product_id, s.qty, 0);

-- Demo order for GLOBEX
MERGE INTO sales_order o
USING (
    SELECT c.tenant_id, 'SO-GLOBEX-0001' AS num, c.customer_id
    FROM customer c
    JOIN tenant t ON t.tenant_id = c.tenant_id
    WHERE t.tenant_code = 'GLOBEX' AND c.customer_number = 'C0001'
) s
ON (o.tenant_id = s.tenant_id AND o.order_number = s.num)
WHEN NOT MATCHED THEN
    INSERT (tenant_id, order_number, customer_id, status, currency, total_amount)
    VALUES (s.tenant_id, s.num, s.customer_id, 'PENDING', 'USD', 99.00);

MERGE INTO sales_order_line l
USING (
    SELECT o.tenant_id, o.order_id, p.product_id
    FROM sales_order o
    JOIN tenant t ON t.tenant_id = o.tenant_id
    JOIN product p ON p.tenant_id = o.tenant_id
    WHERE t.tenant_code = 'GLOBEX' AND o.order_number = 'SO-GLOBEX-0001' AND p.sku = 'PRD-001'
) s
ON (l.tenant_id = s.tenant_id AND l.order_id = s.order_id AND l.product_id = s.product_id)
WHEN NOT MATCHED THEN
    INSERT (tenant_id, order_id, product_id, quantity, unit_price, line_total)
    VALUES (s.tenant_id, s.order_id, s.product_id, 1, 99.00, 99.00);

BEGIN
    pkg_tenant.clear_tenant_context;
END;
/

--=====================================================================
-- INITECH data
--=====================================================================
DECLARE
    v_tenant_id NUMBER;
BEGIN
    SELECT tenant_id INTO v_tenant_id FROM tenant WHERE tenant_code = 'INITECH';
    pkg_tenant.set_tenant_context(v_tenant_id);
END;
/

MERGE INTO customer c
USING (SELECT t.tenant_id, 'C0001' AS num, 'Milton' AS fn, 'Waddams' AS ln, 'milton@initech.com' AS email
       FROM tenant t WHERE t.tenant_code = 'INITECH') s
ON (c.tenant_id = s.tenant_id AND c.customer_number = s.num)
WHEN NOT MATCHED THEN
    INSERT (tenant_id, customer_number, first_name, last_name, email, status)
    VALUES (s.tenant_id, s.num, s.fn, s.ln, s.email, 'ACTIVE');

MERGE INTO product_category pc
USING (SELECT t.tenant_id, 'GENERAL' AS name FROM tenant t WHERE t.tenant_code = 'INITECH') s
ON (pc.tenant_id = s.tenant_id AND pc.name = s.name)
WHEN NOT MATCHED THEN
    INSERT (tenant_id, name, status)
    VALUES (s.tenant_id, s.name, 'ACTIVE');

MERGE INTO warehouse w
USING (SELECT t.tenant_id, 'WH1' AS code, 'Main Warehouse' AS name
       FROM tenant t WHERE t.tenant_code = 'INITECH') s
ON (w.tenant_id = s.tenant_id AND w.code = s.code)
WHEN NOT MATCHED THEN
    INSERT (tenant_id, code, name, location, status)
    VALUES (s.tenant_id, s.code, s.name, 'New Orleans', 'ACTIVE');

MERGE INTO product p
USING (SELECT t.tenant_id, 'PRD-001' AS sku, 'PC Load Letter' AS name, 1200.00 AS price
       FROM tenant t WHERE t.tenant_code = 'INITECH') s
ON (p.tenant_id = s.tenant_id AND p.sku = s.sku)
WHEN NOT MATCHED THEN
    INSERT (tenant_id, sku, name, unit_price, currency, status)
    VALUES (s.tenant_id, s.sku, s.name, s.price, 'USD', 'ACTIVE');

-- Inventory: EVERY product of the tenant
MERGE INTO inventory i
USING (
    SELECT p.tenant_id, w.warehouse_id, p.product_id, 100 AS qty
    FROM product p
    JOIN warehouse w ON w.tenant_id = p.tenant_id
    WHERE p.tenant_id = (SELECT tenant_id FROM tenant WHERE tenant_code = 'INITECH')
) s
ON (i.tenant_id = s.tenant_id AND i.warehouse_id = s.warehouse_id AND i.product_id = s.product_id)
WHEN NOT MATCHED THEN
    INSERT (tenant_id, warehouse_id, product_id, available_quantity, reserved_quantity)
    VALUES (s.tenant_id, s.warehouse_id, s.product_id, s.qty, 0);

BEGIN
    pkg_tenant.clear_tenant_context;
END;
/

--=====================================================================
-- End of seed data (context cleared after every tenant section)
--=====================================================================

-- Explicit commit: sqlplus commits on EXIT, but do not rely on client
-- behavior (Flyway / JDBC runners differ).
COMMIT;
