package com.enterpriseorders.backend.tenant;

import com.enterpriseorders.backend.tenant.dto.TenantResponse;
import com.enterpriseorders.backend.tenant.port.input.CreateTenantUseCase;
import com.enterpriseorders.backend.tenant.port.input.GetTenantQuery;
import com.enterpriseorders.backend.tenant.port.output.TenantRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TenantController.class)
@AutoConfigureMockMvc(addFilters = false)
class TenantControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private CreateTenantUseCase createTenantUseCase;
    @MockBean private GetTenantQuery getTenantQuery;
    @MockBean private TenantRepository tenantRepository;
    @MockBean private TenantContext tenantContext;

    private static final String BASE = "/api/v1/tenants";
    private final TenantResponse mockTenant = new TenantResponse(1L, "ACME", "Acme Corp", "STANDARD", "ACTIVE", "SHARED_SCHEMA");

    @Test
    void shouldCreateTenant() throws Exception {
        when(createTenantUseCase.createTenant(any())).thenReturn(mockTenant);
        mockMvc.perform(post(BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tenantCode\":\"ACME\",\"name\":\"Acme Corp\",\"tier\":\"STANDARD\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantCode").value("ACME"));
    }

    @Test
    void shouldGetTenant() throws Exception {
        when(getTenantQuery.getTenant(1L)).thenReturn(mockTenant);
        mockMvc.perform(get(BASE + "/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Acme Corp"));
    }

    @Test
    void shouldReturn400WhenInvalidTenantRequest() throws Exception {
        mockMvc.perform(post(BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Acme Corp\",\"tier\":\"STANDARD\"}"))
                .andExpect(status().isBadRequest());
    }
}
