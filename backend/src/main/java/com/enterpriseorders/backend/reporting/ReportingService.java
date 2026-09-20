package com.enterpriseorders.backend.reporting;

import com.enterpriseorders.backend.exception.RefreshRateLimitException;
import com.enterpriseorders.backend.reporting.port.output.dto.OrderDailySummary;
import com.enterpriseorders.backend.reporting.port.output.dto.ProductSalesSummary;
import com.enterpriseorders.backend.reporting.infrastructure.RefreshRateLimiter;
import com.enterpriseorders.backend.reporting.port.input.GetDailyOrdersQuery;
import com.enterpriseorders.backend.reporting.port.input.GetTopProductsQuery;
import com.enterpriseorders.backend.reporting.port.input.RefreshMaterializedViewsUseCase;
import com.enterpriseorders.backend.reporting.port.output.ReportingQueryPort;
import com.enterpriseorders.backend.reporting.port.output.RefreshMaterializedViewsPort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ReportingService implements GetDailyOrdersQuery, GetTopProductsQuery, RefreshMaterializedViewsUseCase {

    private final ReportingQueryPort reportingQueryPort;
    private final RefreshMaterializedViewsPort refreshPort;
    private final RefreshRateLimiter rateLimiter;

    public ReportingService(ReportingQueryPort reportingQueryPort, RefreshMaterializedViewsPort refreshPort, RefreshRateLimiter rateLimiter) {
        this.reportingQueryPort = reportingQueryPort;
        this.refreshPort = refreshPort;
        this.rateLimiter = rateLimiter;
    }

    @Override
    public List<OrderDailySummary> getDailyOrders() {
        return reportingQueryPort.getDailyOrders();
    }

    @Override
    public List<ProductSalesSummary> getTopProducts(int limit) {
        return reportingQueryPort.getTopProducts(limit);
    }

    @Override
    public void refreshMaterializedViews() {
        if (!rateLimiter.tryAcquire()) {
            throw new RefreshRateLimitException(
                    "Materialized view refresh rate limit exceeded. Wait " +
                    rateLimiter.getRemainingCooldownMs() + " seconds.");
        }
        refreshPort.refreshMaterializedViews();
    }
}
