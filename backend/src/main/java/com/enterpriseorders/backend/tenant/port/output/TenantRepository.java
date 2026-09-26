package com.enterpriseorders.backend.tenant.port.output;

import com.enterpriseorders.backend.tenant.model.NewTenant;
import com.enterpriseorders.backend.tenant.model.TenantView;

import java.util.Optional;

public interface TenantRepository {
    Optional<Long> findActiveTenantIdByCode(String tenantCode);
    Optional<Long> findActiveTenantIdById(Long tenantId);
    Long create(NewTenant request);
    TenantView findById(Long tenantId);
}
