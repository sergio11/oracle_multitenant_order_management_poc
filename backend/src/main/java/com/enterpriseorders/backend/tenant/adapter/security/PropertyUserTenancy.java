package com.enterpriseorders.backend.tenant.adapter.security;

import com.enterpriseorders.backend.tenant.port.output.UserTenancy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Property-backed {@link UserTenancy} (C4).
 *
 * <p>Reads {@code app.security.tenancy.operator-allowed-tenants} (comma
 * separated, default {@code ACME}). Matching is case-insensitive on trimmed
 * values. Admin users bypass this check in {@code TenantContextFilter}.
 */
@Component
public class PropertyUserTenancy implements UserTenancy {

    private final List<String> operatorAllowedTenants;

    public PropertyUserTenancy(
            @Value("${app.security.tenancy.operator-allowed-tenants:ACME}") List<String> operatorAllowedTenants) {
        this.operatorAllowedTenants = operatorAllowedTenants.stream()
                .map(code -> code == null ? "" : code.trim().toUpperCase())
                .filter(code -> !code.isEmpty())
                .toList();
    }

    @Override
    public boolean isAllowed(String username, String tenantCode) {
        if (username == null || username.isBlank() || tenantCode == null || tenantCode.isBlank()) {
            return false;
        }
        return operatorAllowedTenants.contains(tenantCode.trim().toUpperCase());
    }
}
