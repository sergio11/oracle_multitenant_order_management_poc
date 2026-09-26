package com.enterpriseorders.backend.tenant.port.input;

/**
 * Input port for tenant resolution from the web layer (H4).
 *
 * <p>Implemented by {@code TenantService}; the filter depends on this port,
 * never on the repository directly.
 */
public interface ResolveTenantUseCase {

    /**
     * @return the id of the ACTIVE tenant for the given code
     * @throws com.enterpriseorders.backend.exception.BusinessException with
     *         code {@code TENANT_NOT_FOUND} when the code is unknown or inactive
     */
    long resolveActiveTenantId(String tenantCode);
}
