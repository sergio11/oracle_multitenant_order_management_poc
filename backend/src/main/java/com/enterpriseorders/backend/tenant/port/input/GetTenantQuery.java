package com.enterpriseorders.backend.tenant.port.input;

import com.enterpriseorders.backend.tenant.model.TenantBO;

public interface GetTenantQuery {
    TenantBO getTenant(Long tenantId);
}
