# 03 — Data Model

Tables (V001–V008): `tenant`, `customer(+address,account)`, `product_category`,
`product` (uq tenant+sku), `warehouse` (uq tenant+code),
`inventory` (uq tenant/warehouse/product, VERSION), `sales_order`
(status DRAFT…CANCELLED, VERSION, uq tenant+order_number),
`sales_order_line` (+ V020 `reserved_warehouse_id` FK→warehouse, nullable for
pre-V020 rows with legacy fallback), `order_status_history`, `payment`
(uq tenant+payment_reference; exact-total guard), `audit_event`,
`outbox_event` (V021 dedup uq tenant/aggregate_type/aggregate_id/event_type),
`idempotency_key` (uq tenant+key; V022 payload VARCHAR2(4000 CHAR)).

VPD (16 policies, `verify_vpd_policies.sql`): 13 base (V011) + `POL_IDEMP_TENANT`
(V015) + 2 MV SELECT policies (V014); `TENANT` unprotected (resolution).
V013 exception-safe MV build (drop→create→re-add, fail-closed re-raise).

MVs: `MV_ORDER_DAILY_SUMMARY` (tenant,day → count/revenue/avg),
`MV_PRODUCT_SALES_SUMMARY` (tenant/product/period → qty/revenue/count);
COMPLETE ON DEMAND; refresh proc V018 (definer's rights, context-free).
