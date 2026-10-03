package com.enterpriseorders.backend.tenant.port.output;

import com.enterpriseorders.backend.tenant.model.NewTenantBO;
import com.enterpriseorders.backend.tenant.model.TenantBO;

import java.util.Optional;

public interface TenantRepository {
    Optional<Long> findActiveTenantIdByCode(String tenantCode);
    Long create(NewTenantBO request);
    TenantBO findById(Long tenantId);
}
