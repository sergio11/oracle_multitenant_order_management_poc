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
-- Query rewrite (SRS §43) is deliberately NOT enabled on these MVs:
-- under the default QUERY_REWRITE_INTEGRITY=ENFORCE the optimizer may
-- only use a fully refreshed MV anyway (these are ON DEMAND, so
-- rewrite would almost never fire - inert), while ENABLE QUERY REWRITE
-- turns into a silent stale-data hazard if the integrity mode is ever
-- switched to TRUSTED. SRS §43 asks for the capability to be
-- *evaluated*, which this POC records here instead of forcing it on.
--=====================================================================

--=====================================================================
-- Exception-safe MV build with VPD policy compensation (H1)
--
-- Oracle forbids CREATE MATERIALIZED VIEW over tables protected by
-- DBMS_RLS policies (ORA-30372), so the base-table policies
-- POL_ORDER_TENANT / POL_LINE_TENANT (V011) must be dropped BEFORE the
-- MVs are created, then re-added with IDENTICAL parameters - end state
-- is unchanged, and V014 adds the MV-level policies on top.
--
-- HAZARD (fixed here): a plain statement sequence
--   drop policies -> CREATE MV ... -> re-add policies
-- leaves SALES_ORDER / SALES_ORDER_LINE WITHOUT tenant isolation
-- (fail-open) whenever any statement between the drop and the re-add
-- fails - e.g. CREATE MV raising ORA-00955 on a partially applied
-- re-run, or a grant failure.
--
-- DESIGN: the whole sequence runs in ONE anonymous block. All DDL goes
-- through EXECUTE IMMEDIATE; the WHEN OTHERS handler re-adds BOTH
-- base-table policies (each restore in its own tolerant sub-block so
-- an already-restored policy raises ORA-28105 harmlessly) before
-- RE-RAISING the original error. Flyway still marks V013 as failed,
-- but tenant isolation is restored first: never a fail-open window.
-- MVs from a previous partial attempt are dropped (tolerantly) up
-- front so a repaired re-run starts clean.
--=====================================================================
BEGIN
    -- 1) Temporarily drop the base-table VPD policies (ORA-30372).
    --    Each DROP tolerates missing policies so the block is re-runnable.
    BEGIN
        DBMS_RLS.DROP_POLICY('APP_OWNER','SALES_ORDER',     'POL_ORDER_TENANT');
    EXCEPTION WHEN OTHERS THEN NULL;
    END;
    BEGIN
        DBMS_RLS.DROP_POLICY('APP_OWNER','SALES_ORDER_LINE','POL_LINE_TENANT');
    EXCEPTION WHEN OTHERS THEN NULL;
    END;

    -- 2) Drop MVs left behind by a previous partial attempt (ORA-00942
    --    tolerated on a first run).
    BEGIN
        EXECUTE IMMEDIATE 'DROP MATERIALIZED VIEW mv_order_daily_summary';
    EXCEPTION WHEN OTHERS THEN NULL;
    END;
    BEGIN
        EXECUTE IMMEDIATE 'DROP MATERIALIZED VIEW mv_product_sales_summary';
    EXCEPTION WHEN OTHERS THEN NULL;
    END;

    -- 3) MV_ORDER_DAILY_SUMMARY (SRS §40)
    --    Dimensions: TENANT_ID, ORDER_DATE
    --    Metrics:    ORDER_COUNT, TOTAL_REVENUE, AVERAGE_ORDER_VALUE
    EXECUTE IMMEDIATE
        'CREATE MATERIALIZED VIEW mv_order_daily_summary'
        || ' REFRESH COMPLETE ON DEMAND'
        || ' AS SELECT'
        || '     tenant_id,'
        || '     TRUNC(created_at)                          AS order_date,'
        || '     COUNT(*)                                   AS order_count,'
        || '     SUM(total_amount)                          AS total_revenue,'
        || '     ROUND(AVG(total_amount), 2)                AS average_order_value'
        || ' FROM sales_order'
        || ' WHERE status NOT IN (''DRAFT'', ''CANCELLED'')'
        || ' GROUP BY tenant_id, TRUNC(created_at)';

    EXECUTE IMMEDIATE
        'COMMENT ON MATERIALIZED VIEW mv_order_daily_summary IS'
        || ' ''Daily order summary per tenant (SRS 40). Complete refresh on demand.''';

    -- 4) MV_PRODUCT_SALES_SUMMARY (SRS §41)
    --    Dimensions: TENANT_ID, PRODUCT_ID, PERIOD (TRUNC by day)
    --    Metrics:    QUANTITY_SOLD, REVENUE, ORDER_COUNT
    EXECUTE IMMEDIATE
        'CREATE MATERIALIZED VIEW mv_product_sales_summary'
        || ' REFRESH COMPLETE ON DEMAND'
        || ' AS SELECT'
        || '     l.tenant_id,'
        || '     l.product_id,'
        || '     TRUNC(o.created_at)                        AS period,'
        || '     SUM(l.quantity)                            AS quantity_sold,'
        || '     SUM(l.line_total)                          AS revenue,'
        || '     COUNT(DISTINCT l.order_id)                 AS order_count'
        || ' FROM sales_order_line l'
        || ' JOIN sales_order o'
        || '   ON o.order_id = l.order_id'
        || '  AND o.tenant_id = l.tenant_id'
        || ' WHERE o.status NOT IN (''DRAFT'', ''CANCELLED'')'
        || ' GROUP BY l.tenant_id, l.product_id, TRUNC(o.created_at)';

    EXECUTE IMMEDIATE
        'COMMENT ON MATERIALIZED VIEW mv_product_sales_summary IS'
        || ' ''Product sales summary per tenant and period (SRS 41). Complete refresh on demand.''';

    -- 5) Grants for the reporting/runtime users (least privilege, SRS §59)
    EXECUTE IMMEDIATE 'GRANT SELECT ON mv_order_daily_summary   TO app_reporting';
    EXECUTE IMMEDIATE 'GRANT SELECT ON mv_product_sales_summary TO app_reporting';
    EXECUTE IMMEDIATE 'GRANT SELECT ON mv_order_daily_summary   TO app_runtime';
    EXECUTE IMMEDIATE 'GRANT SELECT ON mv_product_sales_summary TO app_runtime';

    -- 6) Re-add base-table VPD policies (identical to V011 §4).
    --    Deliberately NOT wrapped in an exception handler: a failure
    --    here must reach the compensation handler below.
    DBMS_RLS.ADD_POLICY('APP_OWNER','SALES_ORDER',     'POL_ORDER_TENANT','APP_OWNER','PKG_VPD.TENANT_PREDICATE','SELECT,INSERT,UPDATE,DELETE',TRUE);
    DBMS_RLS.ADD_POLICY('APP_OWNER','SALES_ORDER_LINE','POL_LINE_TENANT', 'APP_OWNER','PKG_VPD.TENANT_PREDICATE','SELECT,INSERT,UPDATE,DELETE',TRUE);
EXCEPTION
    WHEN OTHERS THEN
        -- COMPENSATION: whatever failed above, tenant isolation on the
        -- order tables must be restored BEFORE the error propagates.
        -- Each restore tolerates ORA-28105 (already restored).
        BEGIN
            DBMS_RLS.ADD_POLICY('APP_OWNER','SALES_ORDER','POL_ORDER_TENANT','APP_OWNER','PKG_VPD.TENANT_PREDICATE','SELECT,INSERT,UPDATE,DELETE',TRUE);
        EXCEPTION WHEN OTHERS THEN NULL;
        END;
        BEGIN
            DBMS_RLS.ADD_POLICY('APP_OWNER','SALES_ORDER_LINE','POL_LINE_TENANT','APP_OWNER','PKG_VPD.TENANT_PREDICATE','SELECT,INSERT,UPDATE,DELETE',TRUE);
        EXCEPTION WHEN OTHERS THEN NULL;
        END;
        RAISE;
END;
/
