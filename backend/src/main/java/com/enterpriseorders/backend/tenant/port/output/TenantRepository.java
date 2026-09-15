package com.enterpriseorders.backend.tenant.port.output;

import com.enterpriseorders.backend.tenant.dto.TenantRequest;
import com.enterpriseorders.backend.tenant.dto.TenantResponse;

import java.util.Optional;

public interface TenantRepository {
    Optional<Long> findActiveTenantIdByCode(String tenantCode);
    Optional<Long> findActiveTenantIdById(Long tenantId);
    Long create(TenantRequest request);
    TenantResponse findById(Long tenantId);
}
