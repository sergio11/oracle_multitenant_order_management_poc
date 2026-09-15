package com.enterpriseorders.backend.reporting;

import com.enterpriseorders.backend.reporting.port.output.dto.OrderDailySummary;
import com.enterpriseorders.backend.reporting.port.output.dto.ProductSalesSummary;
import com.enterpriseorders.backend.reporting.port.input.GetDailyOrdersQuery;
import com.enterpriseorders.backend.reporting.port.input.GetTopProductsQuery;
import com.enterpriseorders.backend.reporting.port.input.RefreshMaterializedViewsUseCase;
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

    private final GetDailyOrdersQuery getDailyOrdersQuery;
    private final GetTopProductsQuery getTopProductsQuery;
    private final RefreshMaterializedViewsUseCase refreshMaterializedViewsUseCase;

    public ReportingController(GetDailyOrdersQuery getDailyOrdersQuery,
                               GetTopProductsQuery getTopProductsQuery,
                               RefreshMaterializedViewsUseCase refreshMaterializedViewsUseCase) {
        this.getDailyOrdersQuery = getDailyOrdersQuery;
        this.getTopProductsQuery = getTopProductsQuery;
        this.refreshMaterializedViewsUseCase = refreshMaterializedViewsUseCase;
    }

    @GetMapping("/orders/daily")
    public ResponseEntity<List<OrderDailySummary>> dailyOrders() {
        return ResponseEntity.ok(getDailyOrdersQuery.getDailyOrders());
    }

    @GetMapping("/products/top")
    public ResponseEntity<List<ProductSalesSummary>> topProducts(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(getTopProductsQuery.getTopProducts(limit));
    }

    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh() {
        refreshMaterializedViewsUseCase.refreshMaterializedViews();
        return ResponseEntity.noContent().build();
    }
}
