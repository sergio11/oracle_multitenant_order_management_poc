package com.enterpriseorders.backend.tenant.adapter.web;

import com.enterpriseorders.backend.exception.BusinessException;
import com.enterpriseorders.backend.tenant.port.input.ResolveTenantUseCase;
import com.enterpriseorders.backend.tenant.port.output.TenantContextHolder;
import com.enterpriseorders.backend.tenant.port.output.RoleTenancy;
import com.enterpriseorders.backend.web.error.ApiError;
import com.enterpriseorders.backend.web.error.GlobalExceptionHandler;
import com.enterpriseorders.backend.web.filter.CorrelationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Tenant resolver filter (SRS §13, §52, §57).
 *
 * Pipeline:
 *
 *   Security chain (basic auth, ordered before this filter)
 *        -> X-Tenant-ID header + principal binding (this filter)
 *        -> ResolveTenantUseCase (application layer validates ACTIVE)
 *        -> TenantContextHolder (request-scoped state)
 *        -> TenantAwareDataSource (sets Oracle APP_CTX on the connection)
 *        -> VPD
 *
 * Notes:
 * <ul>
 *   <li>H2: context + MDC are cleared on entry; there is no
 *       shouldNotFilter override — excluded paths still run inside
 *       try/finally so stale state can never leak across requests.</li>
 *   <li>H4: depends on ports (ResolveTenantUseCase, TenantContextHolder),
 *       never on TenantRepository or the concrete TenantContext.</li>
 *   <li>C4: non-admin principals are bound to tenants via RoleTenancy
 *       (role-level allowlist); ROLE_ADMIN may access any tenant.</li>
 *   <li>L5: raw header values are never echoed in bodies; logged truncated
 *       to 30 chars at WARN.</li>
 *   <li>M2: error bodies carry the correlation id MDC-first, header fallback.</li>
 *   <li>M3: exclusion uses exact-or-slash-boundary matching, so e.g.
 *       /api/v1/tenantsExport is NOT excluded.</li>
 * </ul>
 *
 * <p>NOTE: this class carries the single {@code @Component} stereotype for the
 * filter. {@code config.FilterOrderConfig} registers it with explicit order
 * (10) and suppresses Boot's default unordered auto-registration, so the
 * chain executes exactly once.
 */
@Component
public class TenantContextFilter extends OncePerRequestFilter {

    public static final String TENANT_HEADER = "X-Tenant-ID";

    private static final int MAX_LOGGED_TENANT_CHARS = 30;

    private static final Logger log = LoggerFactory.getLogger(TenantContextFilter.class);

    private final ResolveTenantUseCase resolveTenantUseCase;
    private final TenantContextHolder tenantContextHolder;
    private final RoleTenancy roleTenancy;
    private final ObjectMapper objectMapper;

    public TenantContextFilter(ResolveTenantUseCase resolveTenantUseCase,
                               TenantContextHolder tenantContextHolder,
                               RoleTenancy roleTenancy,
                               ObjectMapper objectMapper) {
        this.resolveTenantUseCase = resolveTenantUseCase;
        this.tenantContextHolder = tenantContextHolder;
        this.roleTenancy = roleTenancy;
        this.objectMapper = objectMapper;
    }

    static boolean isExcluded(String path) {
        if (path == null) {
            return false;
        }
        if (path.equals("/api/v1/tenants") || path.startsWith("/api/v1/tenants/")) {
            return true;
        }
        if (path.equals("/internal") || path.startsWith("/internal/")) {
            return true;
        }
        return path.equals("/actuator") || path.startsWith("/actuator/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // H2: clear stale state first — a recycled thread must never inherit
        // a previous request's tenant.
        tenantContextHolder.clear();
        MDC.remove("tenantId");
        try {
            if (isExcluded(request.getRequestURI())) {
                filterChain.doFilter(request, response);
                return;
            }

            String rawHeader = request.getHeader(TENANT_HEADER);
            if (rawHeader == null || rawHeader.isBlank()) {
                writeError(request, response, HttpServletResponse.SC_UNAUTHORIZED,
                        "TENANT_REQUIRED", "Missing X-Tenant-ID header");
                return;
            }
            // Normalize once: the role allowlist and the DB lookup both
            // match on trimmed upper-case codes, so 'acme' must behave
            // exactly like 'ACME' at every downstream check.
            String tenantCode = rawHeader.trim().toUpperCase();

            // C4: bind the tenant to the authenticated principal. The
            // security chain runs first, so a missing Authentication here
            // means unauthenticated. AnonymousAuthenticationToken reports
            // isAuthenticated() == true, so it must be rejected explicitly.
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()
                    || authentication instanceof AnonymousAuthenticationToken
                    || authentication.getName() == null) {
                writeError(request, response, HttpServletResponse.SC_UNAUTHORIZED,
                        "UNAUTHORIZED", "Authentication required");
                return;
            }
            boolean isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
            if (!isAdmin && !roleTenancy.isAllowed(tenantCode)) {
                log.warn("Tenant access denied (user={}, tenant={})",
                        authentication.getName(), truncate(tenantCode));
                writeError(request, response, HttpServletResponse.SC_FORBIDDEN,
                        "TENANT_NOT_ALLOWED", "Tenant not allowed for user");
                return;
            }

            long tenantId;
            try {
                tenantId = resolveTenantUseCase.resolveActiveTenantId(tenantCode);
            } catch (BusinessException e) {
                log.warn("Unknown or inactive tenant (tenant={})", truncate(tenantCode));
                writeError(request, response,
                        GlobalExceptionHandler.statusFor(e.getCode()).value(), e.getCode(), e.getMessage());
                return;
            } catch (Exception e) {
                log.error("Tenant resolution failed", e);
                writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                        "TENANT_RESOLUTION_ERROR", "Tenant resolution temporarily unavailable");
                return;
            }

            tenantContextHolder.setTenantId(tenantId);
            MDC.put("tenantId", String.valueOf(tenantId));
            filterChain.doFilter(request, response);
        } finally {
            tenantContextHolder.clear();
            MDC.remove("tenantId");
        }
    }

    private void writeError(HttpServletRequest request, HttpServletResponse response,
                            int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiError.of(code, message, resolveCorrelationId(request))));
    }

    private void writeError(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiError.of(code, message, MDC.get(CorrelationFilter.MDC_KEY))));
    }

    /**
     * M2: correlation id MDC-first (set by CorrelationFilter), falling back
     * to the inbound header when the MDC is empty (e.g. filter order in
     * tests or direct invocation).
     */
    private String resolveCorrelationId(HttpServletRequest request) {
        String fromMdc = MDC.get(CorrelationFilter.MDC_KEY);
        if (fromMdc != null && !fromMdc.isBlank()) {
            return fromMdc;
        }
        String fromHeader = request.getHeader(CorrelationFilter.CORRELATION_HEADER);
        return (fromHeader == null || fromHeader.isBlank()) ? null : fromHeader;
    }

    private static String truncate(String value) {
        if (value != null && value.length() > MAX_LOGGED_TENANT_CHARS) {
            return value.substring(0, MAX_LOGGED_TENANT_CHARS);
        }
        return value;
    }
}
