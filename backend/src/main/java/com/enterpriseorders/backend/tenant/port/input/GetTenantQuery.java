package com.enterpriseorders.backend.tenant.port.input;

import com.enterpriseorders.backend.tenant.model.TenantView;

public interface GetTenantQuery {
    TenantView getTenant(Long tenantId);
}
