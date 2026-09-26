package com.enterpriseorders.backend.tenant;

import com.enterpriseorders.backend.tenant.port.output.TenantContextHolder;
import com.enterpriseorders.backend.tenant.port.output.TenantContextProvider;
import org.springframework.stereotype.Component;

@Component
public class TenantContext implements TenantContextProvider, TenantContextHolder {

    private final ThreadLocal<Long> currentTenant = new ThreadLocal<>();

    @Override
    public Long getTenantId() {
        return currentTenant.get();
    }

    @Override
    public void setTenantId(Long tenantId) {
        currentTenant.set(tenantId);
    }

    @Override
    public void clear() {
        currentTenant.remove();
    }
}
