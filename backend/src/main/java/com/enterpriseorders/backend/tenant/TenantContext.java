package com.enterpriseorders.backend.tenant;

import com.enterpriseorders.backend.tenant.port.output.TenantContextHolder;
import com.enterpriseorders.backend.tenant.port.output.TenantContextProvider;
import org.springframework.stereotype.Component;

@Component
public class TenantContext implements TenantContextProvider, TenantContextHolder {

    private final ThreadLocal<Long> currentTenant = new ThreadLocal<>();
    private final ThreadLocal<String> currentTenantCode = new ThreadLocal<>();
    private final ThreadLocal<String> currentTenantTier = new ThreadLocal<>();

    @Override
    public Long getTenantId() {
        return currentTenant.get();
    }

    @Override
    public String getTenantCode() {
        return currentTenantCode.get();
    }

    @Override
    public String getTenantTier() {
        return currentTenantTier.get();
    }

    @Override
    public void setTenantId(Long tenantId) {
        currentTenant.set(tenantId);
    }

    @Override
    public void setTenantDetails(Long tenantId, String tenantCode, String tier) {
        currentTenant.set(tenantId);
        currentTenantCode.set(tenantCode);
        currentTenantTier.set(tier);
    }

    @Override
    public void clear() {
        currentTenant.remove();
        currentTenantCode.remove();
        currentTenantTier.remove();
    }
}
