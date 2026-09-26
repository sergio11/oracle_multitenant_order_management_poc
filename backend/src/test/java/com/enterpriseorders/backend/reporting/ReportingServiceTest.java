package com.enterpriseorders.backend.reporting;

import com.enterpriseorders.backend.exception.BusinessException;
import com.enterpriseorders.backend.exception.RefreshRateLimitException;
import com.enterpriseorders.backend.web.error.GlobalExceptionHandler;
import com.enterpriseorders.backend.reporting.application.ReportingService;
import com.enterpriseorders.backend.reporting.port.output.RateLimiterPort;
import com.enterpriseorders.backend.reporting.port.output.RefreshMaterializedViewsPort;
import com.enterpriseorders.backend.reporting.port.output.ReportingQueryPort;
import com.enterpriseorders.backend.reporting.port.output.dto.OrderDailySummary;
import com.enterpriseorders.backend.reporting.port.output.dto.ProductSalesSummary;
import com.enterpriseorders.backend.reporting.port.output.dto.ReportsFreshness;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportingServiceTest {
    @Mock private ReportingQueryPort reportingQueryPort;
    @Mock private RefreshMaterializedViewsPort refreshPort;
    @Mock private RateLimiterPort rateLimiter;
    @InjectMocks private ReportingService reportingService;

    @Test
    void shouldGetDailyOrders() {
        var summary = new OrderDailySummary(1L, LocalDate.now(), 10L, BigDecimal.valueOf(1000), BigDecimal.valueOf(100));
        when(reportingQueryPort.getDailyOrders()).thenReturn(List.of(summary));
        List<OrderDailySummary> result = reportingService.getDailyOrders();
        assertEquals(1, result.size());
        assertEquals(10L, result.get(0).orderCount());
    }

    @Test
    void shouldGetTopProducts() {
        var product = new ProductSalesSummary(1L, 1L, LocalDate.now(), 50L, BigDecimal.valueOf(5000), 20L);
        when(reportingQueryPort.getTopProducts(10)).thenReturn(List.of(product));
        List<ProductSalesSummary> result = reportingService.getTopProducts(10);
        assertEquals(1, result.size());
        verify(reportingQueryPort).getTopProducts(10);
    }

    @Test
    void shouldRejectLimitBelowRange() {
        BusinessException ex = assertThrows(BusinessException.class, () -> reportingService.getTopProducts(0));
        assertEquals("INVALID_LIMIT", ex.getCode());
        assertEquals(400, GlobalExceptionHandler.statusFor(ex.getCode()).value());
        verify(reportingQueryPort, never()).getTopProducts(anyInt());
    }

    @Test
    void shouldRejectLimitAboveRange() {
        BusinessException ex = assertThrows(BusinessException.class, () -> reportingService.getTopProducts(101));
        assertEquals("INVALID_LIMIT", ex.getCode());
        verify(reportingQueryPort, never()).getTopProducts(anyInt());
    }

    @Test
    void shouldAcceptBoundaryLimits() {
        when(reportingQueryPort.getTopProducts(1)).thenReturn(List.of());
        when(reportingQueryPort.getTopProducts(100)).thenReturn(List.of());
        reportingService.getTopProducts(1);
        reportingService.getTopProducts(100);
        verify(reportingQueryPort).getTopProducts(1);
        verify(reportingQueryPort).getTopProducts(100);
    }

    @Test
    void shouldRefreshWhenRateLimiterAllows() {
        when(rateLimiter.tryAcquire()).thenReturn(true);
        reportingService.refreshMaterializedViews();
        verify(refreshPort).refreshMaterializedViews();
        verify(rateLimiter).release();
        verify(rateLimiter, never()).refund();
    }

    @Test
    void shouldThrowWhenRateLimitExceeded() {
        when(rateLimiter.tryAcquire()).thenReturn(false);
        when(rateLimiter.remainingCooldownSeconds()).thenReturn(45L);
        RefreshRateLimitException ex = assertThrows(RefreshRateLimitException.class,
                () -> reportingService.refreshMaterializedViews());
        assertTrue(ex.getMessage().contains("45"));
        verify(refreshPort, never()).refreshMaterializedViews();
        verify(rateLimiter, never()).refund();
    }

    @Test
    void shouldRefundRateLimitWhenRefreshFails() {
        when(rateLimiter.tryAcquire()).thenReturn(true);
        doThrow(new IllegalStateException("db down")).when(refreshPort).refreshMaterializedViews();
        assertThrows(IllegalStateException.class, () -> reportingService.refreshMaterializedViews());
        verify(rateLimiter).refund();
        verify(rateLimiter, never()).release();
    }

    @Test
    void shouldReturnReportsFreshness() {
        Instant last = Instant.parse("2026-09-26T10:00:00Z");
        when(reportingQueryPort.lastRefreshTime()).thenReturn(last);
        ReportsFreshness result = reportingService.getReportsFreshness();
        assertEquals(last, result.lastRefresh());
        assertEquals("MATERIALIZED_VIEW", result.source());
    }

    @Test
    void shouldReturnNullFreshnessWhenNeverRefreshed() {
        when(reportingQueryPort.lastRefreshTime()).thenReturn(null);
        ReportsFreshness result = reportingService.getReportsFreshness();
        assertNull(result.lastRefresh());
        assertEquals("MATERIALIZED_VIEW", result.source());
    }
}
