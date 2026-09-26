# ADR-008 — In-Memory Auth + User→Tenant Binding

Status: Accepted

## Context
Header-only tenant resolution let any operator target any tenant (SRS §13
violation); full IdP was out of scope.

## Decision
Spring Security HTTP Basic, `InMemoryUserDetailsManager` (admin+operator),
BCrypt; `app.security.tenancy.operator-allowed-tenants` (default ACME);
`ADMIN` may access any tenant, others 403 `TENANT_NOT_ALLOWED` on mismatch.
Prod has NO defaults: fail-fast `CredentialHardeningCheck` (<12 chars,
missing, or `changeme` aborts startup). `GET /api/v1/tenants/**` ADMIN-only.

## Consequences
+ Principal+header binding closes the header-only hole. − Residual: not
JWT/LDAP; per-JVM user store; accepted for POC (documented).
