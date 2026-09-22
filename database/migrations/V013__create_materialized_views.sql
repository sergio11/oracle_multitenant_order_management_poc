--=====================================================================
-- V013 - Materialized Views for reporting (SRS §38-43, §98)
--
-- Reporting over OLTP data without running heavy analytical queries
-- directly against the transactional tables.
--
-- Refresh strategies (SRS §42):
--   MV_ORDER_DAILY_SUMMARY     -> COMPLETE REFRESH ON DEMAND
--      Justification: low-volume daily aggregates; complete refresh is
--      simple and reliable for the POC volume.
--   MV_PRODUCT_SALES_SUMMARY   -> COMPLETE REFRESH ON DEMAND
--      Justification: product-period aggregations; complete refresh
--      keeps consistency with bounded data size.
--
-- Query Rewrite (SRS §43) is enabled so the optimizer can transparently
-- route compatible queries to these MVs.
--=====================================================================

--=====================================================================
-- Temporarily drop base-table VPD policies (ORA-30372)
--
-- Oracle forbids CREATE MATERIALIZED VIEW over tables protected by
-- DBMS_RLS policies (V011 put POL_ORDER_TENANT / POL_LINE_TENANT on
-- SALES_ORDER / SALES_ORDER_LINE). Drop them here, create the MVs,
-- then re-add them below with IDENTICAL parameters - end state is
-- unchanged, and V014 adds the MV-level policies on top.
-- Drops tolerate missing policies so this block is re-runnable.
--=====================================================================
BEGIN
    BEGIN DBMS_RLS.DROP_POLICY('APP_OWNER','SALES_ORDER',     'POL_ORDER_TENANT'); EXCEPTION WHEN OTHERS THEN NULL; END;
    BEGIN DBMS_RLS.DROP_POLICY('APP_OWNER','SALES_ORDER_LINE','POL_LINE_TENANT');  EXCEPTION WHEN OTHERS THEN NULL; END;
END;
/

--=====================================================================
-- MV_ORDER_DAILY_SUMMARY (SRS §40)
-- Dimensions: TENANT_ID, ORDER_DATE
-- Metrics:    ORDER_COUNT, TOTAL_REVENUE, AVERAGE_ORDER_VALUE
--=====================================================================
CREATE MATERIALIZED VIEW mv_order_daily_summary
REFRESH COMPLETE ON DEMAND
ENABLE QUERY REWRITE
AS
SELECT
    tenant_id,
    TRUNC(created_at)                                     AS order_date,
    COUNT(*)                                              AS order_count,
    SUM(total_amount)                                     AS total_revenue,
    ROUND(AVG(total_amount), 2)                           AS average_order_value
FROM sales_order
WHERE status NOT IN ('DRAFT','CANCELLED')
GROUP BY tenant_id, TRUNC(created_at);

COMMENT ON MATERIALIZED VIEW mv_order_daily_summary IS
    'Daily order summary per tenant (SRS 40). Complete refresh on demand.';

--=====================================================================
-- MV_PRODUCT_SALES_SUMMARY (SRS §41)
-- Dimensions: TENANT_ID, PRODUCT_ID, PERIOD (TRUNC by day)
-- Metrics:    QUANTITY_SOLD, REVENUE, ORDER_COUNT
--=====================================================================
CREATE MATERIALIZED VIEW mv_product_sales_summary
REFRESH COMPLETE ON DEMAND
ENABLE QUERY REWRITE
AS
SELECT
    l.tenant_id,
    l.product_id,
    TRUNC(o.created_at)                                   AS period,
    SUM(l.quantity)                                       AS quantity_sold,
    SUM(l.line_total)                                     AS revenue,
    COUNT(DISTINCT l.order_id)                            AS order_count
FROM sales_order_line l
JOIN sales_order o
  ON o.order_id = l.order_id
 AND o.tenant_id = l.tenant_id
WHERE o.status NOT IN ('DRAFT','CANCELLED')
GROUP BY l.tenant_id, l.product_id, TRUNC(o.created_at);

COMMENT ON MATERIALIZED VIEW mv_product_sales_summary IS
    'Product sales summary per tenant and period (SRS 41). Complete refresh on demand.';

--=====================================================================
-- Grants for the reporting user (least privilege, SRS §59)
--=====================================================================
GRANT SELECT ON mv_order_daily_summary   TO app_reporting;
GRANT SELECT ON mv_product_sales_summary TO app_reporting;
GRANT SELECT ON mv_order_daily_summary   TO app_runtime;
GRANT SELECT ON mv_product_sales_summary TO app_runtime;

--=====================================================================
-- Re-add base-table VPD policies (identical to V011 §4)
--=====================================================================
BEGIN
    DBMS_RLS.ADD_POLICY('APP_OWNER','SALES_ORDER',     'POL_ORDER_TENANT','APP_OWNER','PKG_VPD.TENANT_PREDICATE','SELECT,INSERT,UPDATE,DELETE',TRUE);
    DBMS_RLS.ADD_POLICY('APP_OWNER','SALES_ORDER_LINE','POL_LINE_TENANT', 'APP_OWNER','PKG_VPD.TENANT_PREDICATE','SELECT,INSERT,UPDATE,DELETE',TRUE);
END;
/