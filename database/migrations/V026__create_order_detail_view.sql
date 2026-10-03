--=====================================================================
-- V026 - V_ORDER_DETAIL: flattened order read for OrderDao
--
-- Rationale: findById used to run two round trips (header query +
-- lines query). This view joins SALES_ORDER with SALES_ORDER_LINE so
-- the read collapses into a SINGLE statement executed by the DAO; the
-- result set is grouped in Java (first row carries the header).
--
-- A plain view, NOT a materialized view, on purpose:
--   * the detail read must observe committed state immediately after
--     each PL/SQL lifecycle mutation (create/add_line/confirm/...);
--     an MV would serve stale data until the next refresh and would
--     add refresh/rate-limit machinery for zero read benefit;
--   * reporting keeps its aggregate MVs (V013/V014), where staleness
--     is an accepted trade-off.
--
-- LEFT JOIN (not INNER): an order without lines must still be
-- readable (DRAFT header with zero lines) - with an inner join the
-- header row would vanish and OrderDao would raise ORDER_NOT_FOUND.
--
-- VPD: no policy needed on the view itself. Unlike MVs (ORA-30372,
-- see V013/V014), a plain view reads through the base tables, so the
-- existing POL_ORDER_TENANT / POL_LINE_TENANT policies (V011) still
-- apply; OrderDao additionally binds tenant_id explicitly.
--=====================================================================

CREATE OR REPLACE VIEW v_order_detail AS
SELECT o.tenant_id,
       o.order_id,
       o.order_number,
       o.customer_id,
       o.status,
       o.currency,
       o.total_amount,
       o.version,
       l.order_line_id,
       l.product_id,
       l.quantity,
       l.unit_price,
       l.line_total
FROM   sales_order o
       LEFT JOIN sales_order_line l
              ON l.order_id = o.order_id
             AND l.tenant_id = o.tenant_id;

COMMENT ON TABLE v_order_detail IS 'Order header + lines flattened for the single-round-trip detail read';

-- Plain GRANT on purpose: if the view is missing the migration must
-- fail loudly (same policy as V017).
GRANT SELECT ON v_order_detail TO app_runtime;
