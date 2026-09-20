package com.enterpriseorders.backend.tenant;

import com.enterpriseorders.backend.tenant.port.output.TenantContextProvider;
import org.springframework.stereotype.Component;

@Component
public class TenantContext implements TenantContextProvider {

    private final ThreadLocal<Long> currentTenant = new ThreadLocal<>();

    @Override
    public Long getTenantId() {
        return currentTenant.get();
    }

    public void setTenantId(Long tenantId) {
        currentTenant.set(tenantId);
    }

    public void clear() {
        currentTenant.remove();
    }
}
