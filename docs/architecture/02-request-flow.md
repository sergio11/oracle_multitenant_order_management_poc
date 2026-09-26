# 02 — Request Flow

Order (current): `Security(-100)` → `Correlation(0)` → `Tenant(10)` →
Controller → port/input → service → port/output → DAO → PL/SQL; VPD filters.

- Security first (Basic auth, `/internal/health**` permitAll,
  `/tenants/**` ADMIN-only, orders/reports need `ORDER_OPERATOR`, refresh
  needs `ADMIN`).
- Correlation: validates `[A-Za-z0-9._-]{1,64}`, else generates UUID; MDC.
- Tenant: entry-clear + try/finally (no `shouldNotFilter`); resolves
  `X-Tenant-ID` via `ResolveTenantUseCase`/`TenantContextHolder`/`UserTenancy`;
  non-admin bound to `operator-allowed-tenants` (default ACME) else 403
  `TENANT_NOT_ALLOWED`; missing context downstream → 503
  `TENANT_CONTEXT_MISSING`. Exclusions use exact-or-slash-boundary match.
- `FilterOrderConfig`: correlation 0, tenant 10 (single execution each).
- DAO borrows from `TenantAwareDataSource` (single-block schema+context);
  VPD enforces tenant; errors mapped by `GlobalExceptionHandler`
  (400/404/405/415…; ORA-* never exposed; MDC-first correlationId).
