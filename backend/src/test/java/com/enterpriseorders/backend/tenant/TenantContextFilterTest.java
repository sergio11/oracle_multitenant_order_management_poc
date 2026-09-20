package com.enterpriseorders.backend.tenant;

import com.enterpriseorders.backend.tenant.port.output.TenantRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.io.IOException;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantContextFilterTest {
    @Mock private TenantRepository tenantRepository;
    @Mock private TenantContext tenantContext;
    @Mock private ObjectMapper objectMapper;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain filterChain;
    @InjectMocks private TenantContextFilter filter;

    @Test
    void shouldSetTenantContextWhenValidHeader() throws ServletException, IOException {
        when(request.getHeader("X-Tenant-ID")).thenReturn("ACME");
        when(request.getRequestURI()).thenReturn("/api/v1/orders");
        when(tenantRepository.findActiveTenantIdByCode("ACME")).thenReturn(Optional.of(1L));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        filter.doFilterInternal(request, response, filterChain);
        verify(tenantContext).setTenantId(1L);
        verify(filterChain).doFilter(request, response);
        verify(tenantContext).clear();
    }

    @Test
    void shouldReturn401WhenMissingHeader() throws ServletException, IOException {
        when(request.getHeader("X-Tenant-ID")).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/api/v1/orders");
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        filter.doFilterInternal(request, response, filterChain);
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void shouldReturn401WhenInvalidTenantCode() throws ServletException, IOException {
        when(request.getHeader("X-Tenant-ID")).thenReturn("UNKNOWN");
        when(request.getRequestURI()).thenReturn("/api/v1/orders");
        when(tenantRepository.findActiveTenantIdByCode("UNKNOWN")).thenReturn(Optional.empty());
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        filter.doFilterInternal(request, response, filterChain);
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void shouldReturn503WhenDbException() throws ServletException, IOException {
        when(request.getHeader("X-Tenant-ID")).thenReturn("ACME");
        when(request.getRequestURI()).thenReturn("/api/v1/orders");
        when(tenantRepository.findActiveTenantIdByCode("ACME")).thenThrow(new RuntimeException("DB down"));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        filter.doFilterInternal(request, response, filterChain);
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void shouldSkipFilterForTenantEndpoints() {
        when(request.getRequestURI()).thenReturn("/api/v1/tenants");
        assertTrue(filter.shouldNotFilter(request));
    }

    @Test
    void shouldSkipFilterForActuator() {
        when(request.getRequestURI()).thenReturn("/actuator/health");
        assertTrue(filter.shouldNotFilter(request));
    }

    @Test
    void shouldNotSkipForOrderEndpoints() {
        when(request.getRequestURI()).thenReturn("/api/v1/orders");
        assertFalse(filter.shouldNotFilter(request));
    }
}
