# 04 — Security

- Users/roles: in-memory `admin` (ADMIN+ORDER_OPERATOR) / `operator`
  (ORDER_OPERATOR), BCrypt; no `changeme` defaults in prod
  (`CredentialHardeningCheck` fails fast on missing/short/`changeme`).
- Tenancy binding: header `X-Tenant-ID` + principal check
  (`operator-allowed-tenants`, default ACME); ADMIN any; else 403
  `TENANT_NOT_ALLOWED`. Header alone insufficient.
- Grants matrix: `APP_OWNER` owns schema/DDL (V001–V022, packages definer's
  rights); `APP_RUNTIME` least-privilege (SELECT + idempotency DML + EXECUTE,
  no direct audit/history/outbox DML — V019); `APP_REPORTING` read-only
  (SELECT + `pkg_tenant`/`pkg_vpd` + refresh proc — V019 revoked
  `pkg_order_management/pkg_inventory/pkg_audit`); SYS bootstraps users.
- Fail-closed: no context → zero rows (VPD) / 503 upstream; session setup
  failure closes connection (no leak).
- Secrets: env vars (`ORACLE_PASSWORD`, `APP_*_PASSWORD`); rake uses temp
  env-file + redacted logs; `.bat` scripts use env vars with validation;
  lab defaults (`LabRuntimePass1` etc.) are local-only, never prod.
