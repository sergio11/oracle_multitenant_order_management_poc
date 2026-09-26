package com.enterpriseorders.backend.security;

import com.enterpriseorders.backend.config.SecurityConfig;
import com.enterpriseorders.backend.order.adapter.web.OrderController;
import com.enterpriseorders.backend.order.model.OrderView;
import com.enterpriseorders.backend.order.port.input.AddOrderLineUseCase;
import com.enterpriseorders.backend.order.port.input.CancelOrderUseCase;
import com.enterpriseorders.backend.order.port.input.ConfirmOrderUseCase;
import com.enterpriseorders.backend.order.port.input.CreateOrderUseCase;
import com.enterpriseorders.backend.order.port.input.GetOrderQuery;
import com.enterpriseorders.backend.order.port.input.RegisterPaymentUseCase;
import com.enterpriseorders.backend.tenant.adapter.web.TenantContextFilter;
import com.enterpriseorders.backend.tenant.adapter.web.TenantController;
import com.enterpriseorders.backend.tenant.model.TenantView;
import com.enterpriseorders.backend.tenant.port.input.CreateTenantUseCase;
import com.enterpriseorders.backend.tenant.port.input.GetTenantQuery;
import com.enterpriseorders.backend.tenant.port.input.ResolveTenantUseCase;
import com.enterpriseorders.backend.tenant.port.output.TenantContextHolder;
import com.enterpriseorders.backend.tenant.port.output.UserTenancy;
import com.enterpriseorders.backend.web.filter.CorrelationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security matrix with filters ENABLED (H5-parcial).
 *
 * <p>Unlike OrderControllerTest (addFilters=false), this slice runs the real
 * Spring Security chain plus the real TenantContextFilter/CorrelationFilter
 * (imported explicitly: @Component filters are NOT auto-scanned by
 * @WebMvcTest, so MockMvc would otherwise miss them). Tenant ports are
 * mocked; @WithMockUser supplies the principal (bypasses password checks).
 */
@WebMvcTest({TenantController.class, OrderController.class})
@Import({SecurityConfig.class, TenantContextFilter.class, CorrelationFilter.class})
@TestPropertySource(properties = {
        "app.security.admin.password=admin-test-password-123",
        "app.security.operator.password=operator-test-password-123",
        "app.security.tenancy.operator-allowed-tenants=ACME"
})
class SecurityMatrixTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private CreateTenantUseCase createTenantUseCase;
    @MockBean private GetTenantQuery getTenantQuery;
    @MockBean private CreateOrderUseCase createOrderUseCase;
    @MockBean private AddOrderLineUseCase addOrderLineUseCase;
    @MockBean private ConfirmOrderUseCase confirmOrderUseCase;
    @MockBean private CancelOrderUseCase cancelOrderUseCase;
    @MockBean private RegisterPaymentUseCase registerPaymentUseCase;
    @MockBean private GetOrderQuery getOrderQuery;
    @MockBean private ResolveTenantUseCase resolveTenantUseCase;
    @MockBean private TenantContextHolder tenantContextHolder;
    @MockBean private UserTenancy userTenancy;

    @Test
    void unauthenticatedRequestReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/orders/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "operator", roles = "ORDER_OPERATOR")
    void operatorCannotCreateTenant403() throws Exception {
        mockMvc.perform(post("/api/v1/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantCode\":\"NEWCO\",\"name\":\"New Co\",\"tier\":\"STANDARD\"}"))
                .andExpect(status().isForbidden());
        verify(createTenantUseCase, never()).createTenant(any());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN", "ORDER_OPERATOR"})
    void adminCanCreateTenant() throws Exception {
        when(createTenantUseCase.createTenant(any())).thenReturn(
                new TenantView(1L, "ACME", "Acme Corp", "STANDARD", "ACTIVE", "SHARED_SCHEMA"));
        mockMvc.perform(post("/api/v1/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantCode\":\"ACME\",\"name\":\"Acme Corp\",\"tier\":\"STANDARD\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantCode").value("ACME"));
    }

    @Test
    @WithMockUser(username = "operator", roles = "ORDER_OPERATOR")
    void operatorWithDisallowedTenantGets403() throws Exception {
        when(userTenancy.isAllowed("operator", "OTHER")).thenReturn(false);
        mockMvc.perform(get("/api/v1/orders/1")
                        .header("X-Tenant-ID", "OTHER")
                        .header("X-Correlation-ID", "corr-disallowed-1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_NOT_ALLOWED"))
                .andExpect(jsonPath("$.correlationId").value("corr-disallowed-1"));
        verify(getOrderQuery, never()).getOrder(anyLong());
    }

    @Test
    @WithMockUser(username = "operator", roles = "ORDER_OPERATOR")
    void operatorWithAllowedTenantPasses() throws Exception {
        when(userTenancy.isAllowed("operator", "ACME")).thenReturn(true);
        when(resolveTenantUseCase.resolveActiveTenantId("ACME")).thenReturn(1L);
        when(getOrderQuery.getOrder(1L)).thenReturn(
                new OrderView(1L, "ORD-001", 100L, "DRAFT", "USD", BigDecimal.ZERO, 0, List.of()));
        mockMvc.perform(get("/api/v1/orders/1")
                        .header("X-Tenant-ID", "ACME"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber").value("ORD-001"));
    }
}
