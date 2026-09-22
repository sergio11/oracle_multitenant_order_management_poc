package com.enterpriseorders.backend.reporting;

import com.enterpriseorders.backend.reporting.port.input.GetDailyOrdersQuery;
import com.enterpriseorders.backend.reporting.port.input.GetTopProductsQuery;
import com.enterpriseorders.backend.reporting.port.input.RefreshMaterializedViewsUseCase;
import com.enterpriseorders.backend.reporting.port.output.dto.OrderDailySummary;
import com.enterpriseorders.backend.reporting.port.output.dto.ProductSalesSummary;
import com.enterpriseorders.backend.tenant.TenantContext;
import com.enterpriseorders.backend.tenant.port.output.TenantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReportingController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReportingControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockBean private GetDailyOrdersQuery getDailyOrdersQuery;
    @MockBean private GetTopProductsQuery getTopProductsQuery;
    @MockBean private RefreshMaterializedViewsUseCase refreshMaterializedViewsUseCase;
    @MockBean private TenantRepository tenantRepository;
    @MockBean private TenantContext tenantContext;

    @Test
    void shouldGetDailyOrders() throws Exception {
        var summary = new OrderDailySummary(1L, LocalDate.now(), 10L, BigDecimal.valueOf(1000), BigDecimal.valueOf(100));
        when(getDailyOrdersQuery.getDailyOrders()).thenReturn(List.of(summary));
        mockMvc.perform(get("/api/v1/reports/orders/daily"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderCount").value(10));
    }

    @Test
    void shouldGetTopProducts() throws Exception {
        var product = new ProductSalesSummary(1L, 1L, LocalDate.now(), 50L, BigDecimal.valueOf(5000), 20L);
        when(getTopProductsQuery.getTopProducts(10)).thenReturn(List.of(product));
        mockMvc.perform(get("/api/v1/reports/products/top").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].revenue").value(5000));
    }

    @Test
    void shouldRefreshViews() throws Exception {
        mockMvc.perform(post("/api/v1/reports/refresh"))
                .andExpect(status().isNoContent());
        verify(refreshMaterializedViewsUseCase).refreshMaterializedViews();
    }
}
