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

@RestController
@RequestMapping("/api/v1/reports")
public class ReportingController {

    private final ReportingService reportingService;

    public ReportingController(ReportingService reportingService) {
        this.reportingService = reportingService;
    }

    @GetMapping("/orders/daily")
    public ResponseEntity<List<OrderDailySummary>> dailyOrders() {
        return ResponseEntity.ok(reportingService.getDailyOrders());
    }

    @GetMapping("/products/top")
    public ResponseEntity<List<ProductSalesSummary>> topProducts(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(reportingService.getTopProducts(limit));
    }

    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh() {
        reportingService.refreshMaterializedViews();
        return ResponseEntity.noContent().build();
    }
}
