package com.enterpriseorders.backend.tenant.port.input;

import com.enterpriseorders.backend.tenant.model.TenantBO;

/**
 * Input port for tenant resolution from the web layer (H4).
 *
 * <p>Implemented by {@code TenantService}; the filter depends on this port,
 * never on the repository directly.
 */
public interface ResolveTenantUseCase {

    /**
     * @return the ACTIVE tenant (id, code, tier) for the given code; the
     *         tier drives DataSource routing (shared vs dedicated PDB)
     * @throws com.enterpriseorders.backend.exception.BusinessException with
     *         code {@code TENANT_NOT_FOUND} when the code is unknown or inactive
     */
    TenantBO resolveActiveTenant(String tenantCode);
}
