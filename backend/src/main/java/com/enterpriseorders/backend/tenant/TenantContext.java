package com.enterpriseorders.backend.tenant;

/**
 * Holds the resolved tenant id for the current request (SRS §13, §52).
 *
 * The value is set by TenantContextFilter from the authenticated identity
 * (for the POC: validated X-Tenant-ID header). It is propagated to the
 * Oracle session via pkg_tenant.set_tenant_context so VPD can enforce
 * isolation (defense in depth, SRS §14).
 */
public final class TenantContext {

    private static final ThreadLocal<Long> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setTenantId(Long tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static Long getTenantId() {
        return CURRENT_TENANT.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
</content>
<task_progress>
- [x] FASE 6: infraestructura backend base (UCP, yaml, filtros, errores)
- [x] FASE 6: TenantContext (ThreadLocal)
- [ ] FASE 6: TenantContextFilter (resuelve y setea tenant + contexto Oracle)
- [ ] FASE 6: TenantDao (JDBC) + TenantService + TenantController
- [ ] FASE 6: Commit fase 6
- [ ] FASE 7 a 12 (resto del plan)
</task_progress>