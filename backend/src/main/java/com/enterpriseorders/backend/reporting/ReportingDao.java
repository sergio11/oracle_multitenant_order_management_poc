package com.enterpriseorders.backend.reporting;

import com.enterpriseorders.backend.tenant.TenantContext;
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
public class ReportingDao {

    private final JdbcTemplate jdbcTemplate;

    public ReportingDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Daily order summary from MV_ORDER_DAILY_SUMMARY (SRS §40).
     */
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
     */
    public void refreshMaterializedViews() {
        jdbcTemplate.execute("BEGIN DBMS_MVIEW.REFRESH('MV_ORDER_DAILY_SUMMARY, MV_PRODUCT_SALES_SUMMARY', 'C'); END;");
    }

    /**
     * DTO for daily order summary.
     */
    public record OrderDailySummary(
            Long tenantId,
            LocalDate orderDate,
            Long orderCount,
            BigDecimal totalRevenue,
            BigDecimal averageOrderValue
    ) {
    }

    /**
     * DTO for product sales summary.
     */
    public record ProductSalesSummary(
            Long tenantId,
            Long productId,
            LocalDate period,
            Long quantitySold,
            BigDecimal revenue,
            Long orderCount
    ) {
    }
}
</content>
<task_progress>
- [x] FASE 8: Migración V013 (materialized views)
- [x] FASE 8: ReportingDao (consulta MVs + refresh on demand)
- [ ] FASE 8: ReportingController (endpoints /api/v1/reports)
- [ ] FASE 8: Commit fase 8
- [ ] FASE 9 a 12 (resto del plan)
</task_progress>