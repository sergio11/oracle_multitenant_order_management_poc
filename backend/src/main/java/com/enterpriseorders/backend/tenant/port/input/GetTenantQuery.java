package com.enterpriseorders.backend.tenant.port.input;

import com.enterpriseorders.backend.tenant.dto.TenantResponse;

public interface GetTenantQuery {
    TenantResponse getTenant(Long tenantId);
}
