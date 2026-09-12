package com.enterpriseorders.backend.reporting;

import com.enterpriseorders.backend.reporting.ReportingDao.OrderDailySummary;
import com.enterpriseorders.backend.reporting.ReportingDao.ProductSalesSummary;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Reporting REST API (SRS §53, §98).
 *
 * GET  /api/v1/reports/orders/daily  -> daily order summary (MV)
 * GET  /api/v1/reports/products/top  -> top products by revenue (MV)
 * POST /api/v1/reports/refresh       -> refresh MVs on demand (SRS §42)
 *
 * All queries are tenant-scoped via VPD (the Oracle session context set
 * from X-Tenant-ID), so reports never leak across tenants.
 */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportingController {

    private final ReportingDao reportingDao;

    public ReportingController(ReportingDao reportingDao) {
        this.reportingDao = reportingDao;
    }

    @GetMapping("/orders/daily")
    public ResponseEntity<List<OrderDailySummary>> dailyOrders() {
        return ResponseEntity.ok(reportingDao.getDailyOrders());
    }

    @GetMapping("/products/top")
    public ResponseEntity<List<ProductSalesSummary>> topProducts(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(reportingDao.getTopProducts(limit));
    }

    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh() {
        reportingDao.refreshMaterializedViews();
        return ResponseEntity.noContent().build();
    }
}