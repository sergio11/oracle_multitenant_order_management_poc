package com.enterpriseorders.backend.tenant.port.output;

public interface TenantContextProvider {
    Long getTenantId();

    default String getTenantCode() {
        return null;
    }

    default String getTenantTier() {
        return null;
    }
}
