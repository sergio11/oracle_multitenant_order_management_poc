package com.enterpriseorders.backend.tenant.port.output;

/**
 * Output port binding the non-admin role to tenants (C4).
 *
 * <p>Binding is <strong>role-level, not per-user</strong>: a single allowlist
 * answers which tenant codes non-admin principals may access. Admin bypass
 * (ROLE_ADMIN may access any tenant) is enforced by
 * {@code TenantContextFilter}, not here. Per-user tenant mapping is an
 * explicit non-goal of this POC (JWT/LDAP-backed identity would own it).
 */
public interface RoleTenancy {

    boolean isAllowed(String tenantCode);
}
