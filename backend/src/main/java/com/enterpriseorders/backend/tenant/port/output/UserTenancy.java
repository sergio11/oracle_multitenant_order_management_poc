package com.enterpriseorders.backend.tenant.port.output;

/**
 * Output port binding users to tenants (C4).
 *
 * <p>Admin bypass (ROLE_ADMIN may access any tenant) is enforced by
 * {@code TenantContextFilter}, not here: this port answers only whether the
 * given non-admin user is allowed into the given tenant code.
 */
public interface UserTenancy {

    boolean isAllowed(String username, String tenantCode);
}
