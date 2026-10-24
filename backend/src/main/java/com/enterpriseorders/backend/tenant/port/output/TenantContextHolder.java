package com.enterpriseorders.backend.tenant.port.output;

/**
 * Output port for request-scoped tenant state (H4).
 *
 * <p>Implemented by {@code TenantContext}; the filter depends on this port,
 * never on the concrete class. {@code TenantContextProvider} (read side,
 * used by TenantAwareDataSource) is intentionally untouched.
 */
public interface TenantContextHolder {

    void setTenantId(Long tenantId);

    default void setTenantDetails(Long tenantId, String tenantCode, String tier) {
        setTenantId(tenantId);
    }

    void clear();
}
