package com.enterpriseorders.backend.tenant.port.input;

import com.enterpriseorders.backend.tenant.dto.TenantRequest;
import com.enterpriseorders.backend.tenant.dto.TenantResponse;

public interface CreateTenantUseCase {
    TenantResponse createTenant(TenantRequest request);
}
