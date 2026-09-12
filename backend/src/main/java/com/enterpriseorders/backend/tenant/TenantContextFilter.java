package com.enterpriseorders.backend.tenant;

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
 *        -> TenantResolver (TenantDao validates existence + ACTIVE)
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

    private final TenantDao tenantDao;
    private final ObjectMapper objectMapper;

    public TenantContextFilter(TenantDao tenantDao, ObjectMapper objectMapper) {
        this.tenantDao = tenantDao;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Tenant provisioning endpoints do not carry a tenant context.
        if (path.startsWith("/api/v1/tenants")) {
            return true;
        }
        // Actuator health/info/metrics are not tenant-scoped.
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

        // Resolve tenant code (POC) -> numeric tenant_id (VPD-safe)
        Long tenantId = tenantDao.findActiveTenantIdByCode(tenantHeader.trim()).orElse(null);
        if (tenantId == null) {
            writeError(response, "TENANT_NOT_FOUND", "Unknown or inactive tenant: " + tenantHeader);
            return;
        }

        TenantContext.setTenantId(tenantId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // Never leak tenant context across pooled threads/requests
            TenantContext.clear();
        }
    }

    /**
     * Writes a consistent ApiError JSON response.
     * Filters cannot throw exceptions handled by @RestControllerAdvice,
     * so the error response is written directly here.
     */
    private void writeError(HttpServletResponse response, String code, String message)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiError.of(code, message, null)));
    }
}
