package com.enterpriseorders.backend.reporting;

import com.enterpriseorders.backend.exception.RefreshRateLimitException;
import com.enterpriseorders.backend.reporting.ReportingDao.OrderDailySummary;
import com.enterpriseorders.backend.reporting.ReportingDao.ProductSalesSummary;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class ReportingService {

    private final ReportingDao reportingDao;
    private final AtomicLong lastRefreshTime = new AtomicLong(0);
    private static final long REFRESH_COOLDOWN_MS = 60_000; // 1 minute

    public ReportingService(ReportingDao reportingDao) {
        this.reportingDao = reportingDao;
    }

    public List<OrderDailySummary> getDailyOrders() {
        return reportingDao.getDailyOrders();
    }

    public List<ProductSalesSummary> getTopProducts(int limit) {
        return reportingDao.getTopProducts(limit);
    }

    public void refreshMaterializedViews() {
        long now = System.currentTimeMillis();
        long lastRefresh = lastRefreshTime.get();
        if (now - lastRefresh < REFRESH_COOLDOWN_MS) {
            throw new RefreshRateLimitException(
                    "Materialized view refresh rate limit exceeded. Wait " +
                    ((REFRESH_COOLDOWN_MS - (now - lastRefresh)) / 1000) + " seconds.");
        }
        if (lastRefreshTime.compareAndSet(lastRefresh, now)) {
            reportingDao.refreshMaterializedViews();
        }
    }
}
