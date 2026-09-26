package com.enterpriseorders.backend.reporting;

import com.enterpriseorders.backend.reporting.adapter.web.ReportingController;
import com.enterpriseorders.backend.reporting.port.input.GetDailyOrdersQuery;
import com.enterpriseorders.backend.reporting.port.input.GetReportsFreshnessQuery;
import com.enterpriseorders.backend.reporting.port.input.GetTopProductsQuery;
import com.enterpriseorders.backend.reporting.port.input.RefreshMaterializedViewsUseCase;
import com.enterpriseorders.backend.reporting.port.output.dto.OrderDailySummary;
import com.enterpriseorders.backend.reporting.port.output.dto.ProductSalesSummary;
import com.enterpriseorders.backend.reporting.port.output.dto.ReportsFreshness;
import com.enterpriseorders.backend.tenant.port.input.ResolveTenantUseCase;
import com.enterpriseorders.backend.tenant.port.output.TenantContextHolder;
import com.enterpriseorders.backend.tenant.port.output.RoleTenancy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReportingController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReportingControllerTest {
    private static final String LAST_REFRESH = "2026-09-26T10:00:00Z";

    @Autowired private MockMvc mockMvc;
    @MockBean private GetDailyOrdersQuery getDailyOrdersQuery;
    @MockBean private GetTopProductsQuery getTopProductsQuery;
    @MockBean private RefreshMaterializedViewsUseCase refreshMaterializedViewsUseCase;
    @MockBean private GetReportsFreshnessQuery getReportsFreshnessQuery;
    @MockBean private TenantContextHolder tenantContextHolder;
    @MockBean private ResolveTenantUseCase resolveTenantUseCase;
    @MockBean private RoleTenancy roleTenancy;

    private void stubFreshness() {
        when(getReportsFreshnessQuery.getReportsFreshness())
                .thenReturn(new ReportsFreshness(Instant.parse(LAST_REFRESH), "MATERIALIZED_VIEW"));
    }

    @Test
    void shouldGetDailyOrders() throws Exception {
        var summary = new OrderDailySummary(1L, LocalDate.now(), 10L, BigDecimal.valueOf(1000), BigDecimal.valueOf(100));
        when(getDailyOrdersQuery.getDailyOrders()).thenReturn(List.of(summary));
        stubFreshness();
        mockMvc.perform(get("/api/v1/reports/orders/daily"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-MV-Last-Refresh", LAST_REFRESH))
                .andExpect(jsonPath("$[0].orderCount").value(10));
    }

    @Test
    void shouldGetTopProducts() throws Exception {
        var product = new ProductSalesSummary(1L, 1L, LocalDate.now(), 50L, BigDecimal.valueOf(5000), 20L);
        when(getTopProductsQuery.getTopProducts(10)).thenReturn(List.of(product));
        stubFreshness();
        mockMvc.perform(get("/api/v1/reports/products/top").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-MV-Last-Refresh", LAST_REFRESH))
                .andExpect(jsonPath("$[0].revenue").value(5000));
    }

    @Test
    void shouldOmitFreshnessHeaderWhenUnknown() throws Exception {
        when(getDailyOrdersQuery.getDailyOrders()).thenReturn(List.of());
        when(getReportsFreshnessQuery.getReportsFreshness())
                .thenReturn(new ReportsFreshness(null, "MATERIALIZED_VIEW"));
        mockMvc.perform(get("/api/v1/reports/orders/daily"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("X-MV-Last-Refresh"));
    }

    @Test
    void shouldGetFreshness() throws Exception {
        stubFreshness();
        mockMvc.perform(get("/api/v1/reports/freshness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastRefresh").value(LAST_REFRESH))
                .andExpect(jsonPath("$.source").value("MATERIALIZED_VIEW"));
    }

    @Test
    void shouldRefreshViews() throws Exception {
        mockMvc.perform(post("/api/v1/reports/refresh"))
                .andExpect(status().isNoContent());
        verify(refreshMaterializedViewsUseCase).refreshMaterializedViews();
    }

    @Test
    void shouldRejectLimitBelowRange() throws Exception {
        stubFreshness();
        mockMvc.perform(get("/api/v1/reports/products/top").param("limit", "0"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(getTopProductsQuery);
    }

    @Test
    void shouldRejectLimitAboveRange() throws Exception {
        stubFreshness();
        mockMvc.perform(get("/api/v1/reports/products/top").param("limit", "101"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(getTopProductsQuery);
    }
}
