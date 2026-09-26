package com.enterpriseorders.backend.tenant.adapter.web;

import com.enterpriseorders.backend.exception.BusinessException;
import com.enterpriseorders.backend.tenant.port.input.ResolveTenantUseCase;
import com.enterpriseorders.backend.tenant.port.output.TenantContextHolder;
import com.enterpriseorders.backend.tenant.port.output.RoleTenancy;
import com.enterpriseorders.backend.web.error.ApiError;
import com.enterpriseorders.backend.web.filter.CorrelationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantContextFilterTest {

    @Mock private ResolveTenantUseCase resolveTenantUseCase;
    @Mock private TenantContextHolder tenantContextHolder;
    @Mock private RoleTenancy roleTenancy;
    @Mock private ObjectMapper objectMapper;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain filterChain;

    private TenantContextFilter filter;

    @BeforeEach
    void setUp() {
        filter = new TenantContextFilter(resolveTenantUseCase, tenantContextHolder, roleTenancy, objectMapper);
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    private void stubWriter() throws Exception {
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
    }

    private void stubRequest(String uri, String tenantHeader) {
        when(request.getRequestURI()).thenReturn(uri);
        when(request.getHeader(TenantContextFilter.TENANT_HEADER)).thenReturn(tenantHeader);
    }

    private void authenticate(String username, String... roles) {
        var authorities = List.of(roles).stream()
                .map(SimpleGrantedAuthority::new)
                .map(a -> (org.springframework.security.core.GrantedAuthority) a)
                .toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "pw", authorities));
    }

    @Test
    void shouldResolveAndChainWhenAdminAnyTenant() throws Exception {
        stubRequest("/api/v1/orders", "OTHER");
        authenticate("admin", "ROLE_ADMIN");
        when(resolveTenantUseCase.resolveActiveTenantId("OTHER")).thenReturn(7L);

        filter.doFilterInternal(request, response, filterChain);

        verify(tenantContextHolder).setTenantId(7L);
        verify(filterChain).doFilter(request, response);
        verify(tenantContextHolder, atLeastOnce()).clear();
        // Admin bypass: tenancy port must not gate admins.
        verify(roleTenancy, never()).isAllowed(anyString());
    }

    @Test
    void shouldChainWhenOperatorAllowed() throws Exception {
        stubRequest("/api/v1/orders", "ACME");
        authenticate("operator", "ROLE_ORDER_OPERATOR");
        when(roleTenancy.isAllowed("ACME")).thenReturn(true);
        when(resolveTenantUseCase.resolveActiveTenantId("ACME")).thenReturn(1L);

        filter.doFilterInternal(request, response, filterChain);

        verify(tenantContextHolder).setTenantId(1L);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldReturn403WhenOperatorDisallowed() throws Exception {
        stubRequest("/api/v1/orders", "OTHER");
        stubWriter();
        authenticate("operator", "ROLE_ORDER_OPERATOR");
        when(roleTenancy.isAllowed("OTHER")).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(filterChain, never()).doFilter(any(), any());
        verify(tenantContextHolder, never()).setTenantId(anyLong());
        verify(resolveTenantUseCase, never()).resolveActiveTenantId(anyString());
        var body = ArgumentCaptor.forClass(ApiError.class);
        verify(objectMapper).writeValueAsString(body.capture());
        assertEquals("TENANT_NOT_ALLOWED", body.getValue().code());
    }

    @Test
    void shouldReturn401WhenUnauthenticated() throws Exception {
        stubRequest("/api/v1/orders", "ACME");
        stubWriter();
        // No Authentication in the context.

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(any(), any());
        verify(resolveTenantUseCase, never()).resolveActiveTenantId(anyString());
    }

    @Test
    void shouldReturn401WhenAnonymous() throws Exception {
        stubRequest("/api/v1/orders", "ACME");
        stubWriter();
        // AnonymousAuthenticationToken reports isAuthenticated() == true,
        // so the filter must reject it explicitly.
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymousUser",
                        List.of(new SimpleGrantedAuthority("ROLE_ORDER_OPERATOR"))));

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(any(), any());
        verify(resolveTenantUseCase, never()).resolveActiveTenantId(anyString());
        verify(roleTenancy, never()).isAllowed(anyString());
    }

    @Test
    void shouldNormalizeLowercaseTenantCode() throws Exception {
        stubRequest("/api/v1/orders", "acme");
        authenticate("operator", "ROLE_ORDER_OPERATOR");
        when(roleTenancy.isAllowed("ACME")).thenReturn(true);
        when(resolveTenantUseCase.resolveActiveTenantId("ACME")).thenReturn(1L);

        filter.doFilterInternal(request, response, filterChain);

        verify(tenantContextHolder).setTenantId(1L);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldReturn401WhenMissingHeader() throws Exception {
        stubRequest("/api/v1/orders", null);
        stubWriter();
        authenticate("operator", "ROLE_ORDER_OPERATOR");

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(any(), any());
        var body = ArgumentCaptor.forClass(ApiError.class);
        verify(objectMapper).writeValueAsString(body.capture());
        assertEquals("TENANT_REQUIRED", body.getValue().code());
    }

    @Test
    void shouldReturn401WithoutRawEchoWhenUnknownTenant() throws Exception {
        stubRequest("/api/v1/orders", "EVIL_TENANT_XYZ");
        stubWriter();
        authenticate("operator", "ROLE_ORDER_OPERATOR");
        when(roleTenancy.isAllowed("EVIL_TENANT_XYZ")).thenReturn(true);
        when(resolveTenantUseCase.resolveActiveTenantId("EVIL_TENANT_XYZ"))
                .thenThrow(new BusinessException("TENANT_ACCESS_DENIED",
                        "Unknown or inactive tenant"));

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(any(), any());
        var body = ArgumentCaptor.forClass(ApiError.class);
        verify(objectMapper).writeValueAsString(body.capture());
        assertEquals("TENANT_ACCESS_DENIED", body.getValue().code());
        assertFalse(body.getValue().message().contains("EVIL_TENANT_XYZ"),
                "L5: raw header must not be echoed in the body");
    }

    @Test
    void shouldReturn503WhenDbException() throws Exception {
        stubRequest("/api/v1/orders", "ACME");
        stubWriter();
        authenticate("operator", "ROLE_ORDER_OPERATOR");
        when(roleTenancy.isAllowed("ACME")).thenReturn(true);
        when(resolveTenantUseCase.resolveActiveTenantId("ACME")).thenThrow(new RuntimeException("DB down"));

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void shouldUseMdcCorrelationIdFirst() throws Exception {
        stubRequest("/api/v1/orders", null);
        stubWriter();
        MDC.put(CorrelationFilter.MDC_KEY, "mdc-corr-1");
        // MDC wins, so the header is never read — lenient to survive strict stubs.
        lenient().when(request.getHeader(CorrelationFilter.CORRELATION_HEADER)).thenReturn("header-corr-1");
        authenticate("operator", "ROLE_ORDER_OPERATOR");

        filter.doFilterInternal(request, response, filterChain);

        var body = ArgumentCaptor.forClass(ApiError.class);
        verify(objectMapper).writeValueAsString(body.capture());
        assertEquals("mdc-corr-1", body.getValue().correlationId());
    }

    @Test
    void shouldFallBackToHeaderCorrelationId() throws Exception {
        stubRequest("/api/v1/orders", null);
        stubWriter();
        when(request.getHeader(CorrelationFilter.CORRELATION_HEADER)).thenReturn("header-corr-2");
        authenticate("operator", "ROLE_ORDER_OPERATOR");

        filter.doFilterInternal(request, response, filterChain);

        var body = ArgumentCaptor.forClass(ApiError.class);
        verify(objectMapper).writeValueAsString(body.capture());
        assertEquals("header-corr-2", body.getValue().correlationId());
    }

    @Test
    void shouldSkipButClearForTenantEndpoints() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/tenants");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(resolveTenantUseCase, never()).resolveActiveTenantId(anyString());
        verify(tenantContextHolder, atLeastOnce()).clear();
    }

    @Test
    void shouldSkipNestedTenantPath() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/tenants/1");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(resolveTenantUseCase, never()).resolveActiveTenantId(anyString());
    }

    @Test
    void shouldNotSkipTenantsExportPrefix() throws Exception {
        // M3: boundary matching — /api/v1/tenantsExport is NOT excluded.
        stubRequest("/api/v1/tenantsExport", null);
        stubWriter();
        authenticate("operator", "ROLE_ORDER_OPERATOR");

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void shouldSkipInternalAndActuatorButClear() throws Exception {
        when(request.getRequestURI()).thenReturn("/internal/health/liveness");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(tenantContextHolder, atLeastOnce()).clear();

        when(request.getRequestURI()).thenReturn("/actuator/health");
        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, times(2)).doFilter(request, response);
    }

    @Test
    void shouldClearStaleStateOnEntry() throws Exception {
        stubRequest("/api/v1/orders", null);
        stubWriter();
        authenticate("operator", "ROLE_ORDER_OPERATOR");

        filter.doFilterInternal(request, response, filterChain);

        // Entry clear + finally clear.
        verify(tenantContextHolder, atLeast(2)).clear();
    }

    @Test
    void shouldClearRealContextWhenChainThrows() throws Exception {
        var realContext = new com.enterpriseorders.backend.tenant.TenantContext();
        var realFilter = new TenantContextFilter(resolveTenantUseCase, realContext, roleTenancy, objectMapper);
        stubRequest("/api/v1/orders", "ACME");
        authenticate("operator", "ROLE_ORDER_OPERATOR");
        when(roleTenancy.isAllowed("ACME")).thenReturn(true);
        when(resolveTenantUseCase.resolveActiveTenantId("ACME")).thenReturn(1L);
        doThrow(new RuntimeException("downstream failure")).when(filterChain).doFilter(request, response);

        assertThrows(RuntimeException.class, () -> realFilter.doFilterInternal(request, response, filterChain));

        assertNull(realContext.getTenantId());
    }
}
