package com.enterpriseorders.backend.reporting;

import com.enterpriseorders.backend.exception.RefreshRateLimitException;
import com.enterpriseorders.backend.reporting.infrastructure.RefreshRateLimiter;
import com.enterpriseorders.backend.reporting.port.output.RefreshMaterializedViewsPort;
import com.enterpriseorders.backend.reporting.port.output.ReportingQueryPort;
import com.enterpriseorders.backend.reporting.port.output.dto.OrderDailySummary;
import com.enterpriseorders.backend.reporting.port.output.dto.ProductSalesSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportingServiceTest {
    @Mock private ReportingQueryPort reportingQueryPort;
    @Mock private RefreshMaterializedViewsPort refreshPort;
    @Mock private RefreshRateLimiter rateLimiter;
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
    void shouldRefreshWhenRateLimiterAllows() {
        when(rateLimiter.tryAcquire()).thenReturn(true);
        reportingService.refreshMaterializedViews();
        verify(refreshPort).refreshMaterializedViews();
    }

    @Test
    void shouldThrowWhenRateLimitExceeded() {
        when(rateLimiter.tryAcquire()).thenReturn(false);
        when(rateLimiter.getRemainingCooldownMs()).thenReturn(45L);
        assertThrows(RefreshRateLimitException.class, () -> reportingService.refreshMaterializedViews());
        verify(refreshPort, never()).refreshMaterializedViews();
    }
}
