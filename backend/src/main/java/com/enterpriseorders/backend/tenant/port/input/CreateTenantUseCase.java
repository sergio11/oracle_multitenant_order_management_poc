package com.enterpriseorders.backend.tenant.port.input;

import com.enterpriseorders.backend.tenant.model.NewTenant;
import com.enterpriseorders.backend.tenant.model.TenantView;

public interface CreateTenantUseCase {
    TenantView createTenant(NewTenant request);
}
