package com.enterpriseorders.backend.tenant;

import com.enterpriseorders.backend.tenant.port.output.TenantRepository;
import com.enterpriseorders.backend.web.error.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Tenant resolver filter (SRS §13, §52, §57).
 *
 * For this local POC, the tenant is resolved from the validated
 * X-Tenant-ID header. In a production setup this would come from the
 * authenticated JWT/identity; the design keeps the same pipeline:
 *
 *   Auth/JWT or X-Tenant-ID
 *        -> TenantResolver (TenantRepository validates existence + ACTIVE)
 *        -> TenantContext (ThreadLocal)
 *        -> TenantAwareDataSource (sets Oracle APP_CTX on the connection)
 *        -> VPD
 *
 * Endpoints that do not require a tenant (e.g. POST /api/v1/tenants to
 * create one) are excluded via request path check.
 */
@Component
public class TenantContextFilter extends OncePerRequestFilter {

    public static final String TENANT_HEADER = "X-Tenant-ID";

    private final TenantRepository tenantRepository;
    private final TenantContext tenantContext;
    private final ObjectMapper objectMapper;

    public TenantContextFilter(TenantRepository tenantRepository, TenantContext tenantContext, ObjectMapper objectMapper) {
        this.tenantRepository = tenantRepository;
        this.tenantContext = tenantContext;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.startsWith("/api/v1/tenants")) {
            return true;
        }
        if (path.startsWith("/actuator")) {
            return true;
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String tenantHeader = request.getHeader(TENANT_HEADER);
        if (tenantHeader == null || tenantHeader.isBlank()) {
            writeError(response, "TENANT_REQUIRED", "Missing X-Tenant-ID header");
            return;
        }

        Long tenantId;
        try {
            tenantId = tenantRepository.findActiveTenantIdByCode(tenantHeader.trim()).orElse(null);
        } catch (Exception e) {
            writeError(response, "TENANT_RESOLUTION_ERROR", "Tenant resolution temporarily unavailable");
            return;
        }
        if (tenantId == null) {
            writeError(response, "TENANT_NOT_FOUND", "Unknown or inactive tenant: " + tenantHeader);
            return;
        }

        tenantContext.setTenantId(tenantId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            tenantContext.clear();
        }
    }

    private void writeError(HttpServletResponse response, String code, String message)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiError.of(code, message, null)));
    }
}
