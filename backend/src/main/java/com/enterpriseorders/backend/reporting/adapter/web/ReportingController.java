package com.enterpriseorders.backend.reporting.adapter.web;

import com.enterpriseorders.backend.reporting.port.output.dto.OrderDailySummary;
import com.enterpriseorders.backend.reporting.port.output.dto.ProductSalesSummary;
import com.enterpriseorders.backend.reporting.port.output.dto.ReportsFreshness;
import com.enterpriseorders.backend.reporting.port.input.GetDailyOrdersQuery;
import com.enterpriseorders.backend.reporting.port.input.GetReportsFreshnessQuery;
import com.enterpriseorders.backend.reporting.port.input.GetTopProductsQuery;
import com.enterpriseorders.backend.reporting.port.input.RefreshMaterializedViewsUseCase;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reports")
@Validated
public class ReportingController {

    private final GetDailyOrdersQuery getDailyOrdersQuery;
    private final GetTopProductsQuery getTopProductsQuery;
    private final RefreshMaterializedViewsUseCase refreshMaterializedViewsUseCase;
    private final GetReportsFreshnessQuery getReportsFreshnessQuery;

    public ReportingController(GetDailyOrdersQuery getDailyOrdersQuery,
                               GetTopProductsQuery getTopProductsQuery,
                               RefreshMaterializedViewsUseCase refreshMaterializedViewsUseCase,
                               GetReportsFreshnessQuery getReportsFreshnessQuery) {
        this.getDailyOrdersQuery = getDailyOrdersQuery;
        this.getTopProductsQuery = getTopProductsQuery;
        this.refreshMaterializedViewsUseCase = refreshMaterializedViewsUseCase;
        this.getReportsFreshnessQuery = getReportsFreshnessQuery;
    }

    @GetMapping("/orders/daily")
    public ResponseEntity<List<OrderDailySummary>> dailyOrders() {
        return withFreshness(ResponseEntity.ok()).body(getDailyOrdersQuery.getDailyOrders());
    }

    @GetMapping("/products/top")
    public ResponseEntity<List<ProductSalesSummary>> topProducts(
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit) {
        return withFreshness(ResponseEntity.ok()).body(getTopProductsQuery.getTopProducts(limit));
    }

    /**
     * Freshness of the reporting MVs (M15). Additive endpoint — existing
     * report payloads are untouched; the same signal is also exposed as the
     * {@code X-MV-Last-Refresh} header on the two report GETs.
     */
    @GetMapping("/freshness")
    public ResponseEntity<ReportsFreshness> freshness() {
        return ResponseEntity.ok(getReportsFreshnessQuery.getReportsFreshness());
    }

    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh() {
        refreshMaterializedViewsUseCase.refreshMaterializedViews();
        return ResponseEntity.noContent().build();
    }

    /**
     * Adds {@code X-MV-Last-Refresh: <iso8601>} without changing any response
     * body shape. Header omitted when the refresh time is unknown or when the
     * freshness lookup fails (report reads must not break because of it).
     */
    private ResponseEntity.BodyBuilder withFreshness(ResponseEntity.BodyBuilder builder) {
        try {
            ReportsFreshness freshness = getReportsFreshnessQuery.getReportsFreshness();
            if (freshness != null && freshness.lastRefresh() != null) {
                builder.header("X-MV-Last-Refresh", freshness.lastRefresh().toString());
            }
        } catch (RuntimeException ignored) {
            // Best-effort freshness signal only; never fail a report read.
        }
        return builder;
    }
}
