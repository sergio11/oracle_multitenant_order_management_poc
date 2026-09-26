# ADR-008 — In-Memory Auth + Role→Tenant Binding

Status: Accepted

## Context
Header-only tenant resolution let any operator target any tenant (SRS §13
violation); full IdP was out of scope.

## Decision
Spring Security HTTP Basic, `InMemoryUserDetailsManager` (admin+operator),
BCrypt; `app.security.tenancy.operator-allowed-tenants` (default ACME).
Binding is **role-level, not per-user**: one allowlist answers which tenant
codes non-admin principals may enter (`tenant.port.output.RoleTenancy`,
implemented by `PropertyRoleTenancy`); `ADMIN` may access any tenant, others
403 `TENANT_NOT_ALLOWED` on mismatch. The tenant code is normalized once
(trim + upper-case) in `TenantContextFilter`, and
`AnonymousAuthenticationToken` is rejected explicitly (it reports
`isAuthenticated() == true`).
Prod has NO defaults: fail-fast `CredentialHardeningCheck` (<12 chars,
missing, or `changeme` aborts startup). `GET /api/v1/tenants/**` ADMIN-only.

## Consequences
+ Principal+header binding closes the header-only hole. − Residual: not
JWT/LDAP; per-JVM user store; no per-user tenant mapping; accepted for POC
(documented).
