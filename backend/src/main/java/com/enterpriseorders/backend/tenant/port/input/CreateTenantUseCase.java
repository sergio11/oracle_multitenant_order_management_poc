package com.enterpriseorders.backend.tenant.port.input;

import com.enterpriseorders.backend.tenant.model.NewTenantBO;
import com.enterpriseorders.backend.tenant.model.TenantBO;

public interface CreateTenantUseCase {
    TenantBO createTenant(NewTenantBO request);
}
