package com.enterpriseorders.backend.reporting.application;

import com.enterpriseorders.backend.exception.BusinessException;
import com.enterpriseorders.backend.exception.RefreshRateLimitException;
import com.enterpriseorders.backend.reporting.port.output.dto.OrderDailySummary;
import com.enterpriseorders.backend.reporting.port.output.dto.ProductSalesSummary;
import com.enterpriseorders.backend.reporting.port.output.dto.ReportsFreshness;
import com.enterpriseorders.backend.reporting.port.input.GetDailyOrdersQuery;
import com.enterpriseorders.backend.reporting.port.input.GetReportsFreshnessQuery;
import com.enterpriseorders.backend.reporting.port.input.GetTopProductsQuery;
import com.enterpriseorders.backend.reporting.port.input.RefreshMaterializedViewsUseCase;
import com.enterpriseorders.backend.reporting.port.output.RateLimiterPort;
import com.enterpriseorders.backend.reporting.port.output.ReportingQueryPort;
import com.enterpriseorders.backend.reporting.port.output.RefreshMaterializedViewsPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ReportingService implements GetDailyOrdersQuery, GetTopProductsQuery,
        RefreshMaterializedViewsUseCase, GetReportsFreshnessQuery {

    static final int MIN_LIMIT = 1;
    static final int MAX_LIMIT = 100;
    static final String FRESHNESS_SOURCE = "MATERIALIZED_VIEW";

    private final ReportingQueryPort reportingQueryPort;
    private final RefreshMaterializedViewsPort refreshPort;
    private final RateLimiterPort rateLimiter;

    public ReportingService(ReportingQueryPort reportingQueryPort, RefreshMaterializedViewsPort refreshPort, RateLimiterPort rateLimiter) {
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
        if (limit < MIN_LIMIT || limit > MAX_LIMIT) {
            throw new BusinessException("INVALID_LIMIT",
                    "limit must be between " + MIN_LIMIT + " and " + MAX_LIMIT + " (got " + limit + ")");
        }
        return reportingQueryPort.getTopProducts(limit);
    }

    @Override
    public ReportsFreshness getReportsFreshness() {
        return new ReportsFreshness(reportingQueryPort.lastRefreshTime(), FRESHNESS_SOURCE);
    }

    @Override
    @Transactional
    public void refreshMaterializedViews() {
        if (!rateLimiter.tryAcquire()) {
            throw new RefreshRateLimitException(
                    "Materialized view refresh rate limit exceeded. Wait " +
                    rateLimiter.remainingCooldownSeconds() + " seconds.");
        }
        try {
            refreshPort.refreshMaterializedViews();
            rateLimiter.release();
        } catch (RuntimeException ex) {
            rateLimiter.refund();
            throw ex;
        }
    }
}
