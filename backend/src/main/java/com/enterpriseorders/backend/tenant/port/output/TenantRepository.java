package com.enterpriseorders.backend.tenant.port.output;

import com.enterpriseorders.backend.tenant.model.NewTenantBO;
import com.enterpriseorders.backend.tenant.model.TenantBO;

import java.util.Optional;

public interface TenantRepository {
    /**
     * Reads the ACTIVE tenant row, including its {@code tier}. The tier is
     * the single source of truth for PDB routing: it must come from the
     * database (shared PDB), never be inferred from the tenant code.
     */
    Optional<TenantBO> findActiveByCode(String tenantCode);
    Long create(NewTenantBO request);
    TenantBO findById(Long tenantId);
}
