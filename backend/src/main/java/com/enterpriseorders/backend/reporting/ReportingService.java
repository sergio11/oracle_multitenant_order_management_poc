package com.enterpriseorders.backend.reporting;

import com.enterpriseorders.backend.exception.RefreshRateLimitException;
import com.enterpriseorders.backend.reporting.ReportingDao.OrderDailySummary;
import com.enterpriseorders.backend.reporting.ReportingDao.ProductSalesSummary;
import com.enterpriseorders.backend.reporting.infrastructure.RefreshRateLimiter;
import com.enterpriseorders.backend.reporting.port.input.GetDailyOrdersQuery;
import com.enterpriseorders.backend.reporting.port.input.GetTopProductsQuery;
import com.enterpriseorders.backend.reporting.port.input.RefreshMaterializedViewsUseCase;
import com.enterpriseorders.backend.reporting.port.output.ReportingQueryPort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ReportingService implements GetDailyOrdersQuery, GetTopProductsQuery, RefreshMaterializedViewsUseCase {

    private final ReportingQueryPort reportingQueryPort;
    private final RefreshRateLimiter rateLimiter;

    public ReportingService(ReportingQueryPort reportingQueryPort, RefreshRateLimiter rateLimiter) {
        this.reportingQueryPort = reportingQueryPort;
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
        if (!rateLimiter.canRefresh()) {
            throw new RefreshRateLimitException(
                    "Materialized view refresh rate limit exceeded. Wait " +
                    rateLimiter.getRemainingCooldownMs() + " seconds.");
        }
        rateLimiter.recordRefresh();
        reportingQueryPort.refreshMaterializedViews();
    }
}
