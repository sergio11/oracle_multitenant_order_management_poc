package com.enterpriseorders.backend.reporting;

import com.enterpriseorders.backend.reporting.port.output.RefreshMaterializedViewsPort;
import com.enterpriseorders.backend.reporting.port.output.ReportingQueryPort;
import com.enterpriseorders.backend.reporting.port.output.dto.OrderDailySummary;
import com.enterpriseorders.backend.reporting.port.output.dto.ProductSalesSummary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * JDBC repository for reporting over Materialized Views (SRS §38-43, §98).
 *
 * Queries always include the tenant context filter (via VPD predicate
 * appended by Oracle), so reports are automatically tenant-isolated.
 */
@Repository
public class ReportingDao implements ReportingQueryPort, RefreshMaterializedViewsPort {

    private final JdbcTemplate jdbcTemplate;

    public ReportingDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Daily order summary from MV_ORDER_DAILY_SUMMARY (SRS §40).
     */
    @Override
    public List<OrderDailySummary> getDailyOrders() {
        return jdbcTemplate.query(
                "SELECT tenant_id, order_date, order_count, total_revenue, " +
                "       average_order_value " +
                "FROM mv_order_daily_summary ORDER BY order_date DESC",
                (rs, rowNum) -> new OrderDailySummary(
                        rs.getLong("tenant_id"),
                        rs.getDate("order_date").toLocalDate(),
                        rs.getLong("order_count"),
                        rs.getBigDecimal("total_revenue"),
                        rs.getBigDecimal("average_order_value")));
    }

    /**
     * Top products from MV_PRODUCT_SALES_SUMMARY (SRS §41), limited by a
     * default of 10.
     */
    @Override
    public List<ProductSalesSummary> getTopProducts(int limit) {
        return jdbcTemplate.query(
                "SELECT tenant_id, product_id, period, quantity_sold, revenue, " +
                "       order_count " +
                "FROM ( " +
                "  SELECT tenant_id, product_id, period, quantity_sold, revenue, " +
                "         order_count, " +
                "         ROW_NUMBER() OVER (PARTITION BY tenant_id ORDER BY revenue DESC) rn " +
                "  FROM mv_product_sales_summary " +
                ") WHERE rn <= ? ORDER BY revenue DESC",
                (rs, rowNum) -> new ProductSalesSummary(
                        rs.getLong("tenant_id"),
                        rs.getLong("product_id"),
                        rs.getDate("period").toLocalDate(),
                        rs.getLong("quantity_sold"),
                        rs.getBigDecimal("revenue"),
                        rs.getLong("order_count")),
                limit);
    }

    /**
     * Refreshes the reporting materialized views (SRS §42).
     * Called on demand to bring MVs up to date with OLTP.
     *
     * NOTE: never call DBMS_MVIEW.REFRESH directly as APP_RUNTIME -
     * Oracle raises ORA-30372 because the master tables carry VPD
     * policies. Refresh goes through the definer's-rights procedure
     * refresh_reporting_views (V018), which runs exempt via APP_OWNER.
     *
     * NOTE 2: the refresh MUST run without a tenant context set - an
     * active APP_CTX context breaks the refresh (ORA-01031 inside
     * DBMS_SNAPSHOT). Clear first, refresh second, in a SINGLE block:
     * the DataSource re-applies the request context on every borrow,
     * so two separate statements would refresh with context set.
     * Refresh is a cross-tenant admin operation; callers needing a
     * context must set it again afterwards.
     */
    @Override
    public void refreshMaterializedViews() {
        jdbcTemplate.execute("BEGIN pkg_tenant.clear_tenant_context; refresh_reporting_views; END;");
    }

}
