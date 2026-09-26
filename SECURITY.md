# Security Policy

This is a **POC / portfolio experiment**, not production software. This policy
exists so that nobody gets hurt running the lab, and so that nothing in this
repo is ever mistaken for a production deployment.

## Lab credentials are public demo values

The repository contains hard-coded **lab-only** credentials
(`changeme`, `ChangeMe123!`, `LabOwnerPass1`, `LabRuntimePass1`,
`LabReportPass1`) in `Rakefile` (`DEFAULTS`), `infrastructure/podman/.env.example`,
`infrastructure/podman/scripts/01-init-db.sql` and README curl examples.

- They exist so `rake qa:deploy` works out of the box for a reviewer.
- Treat them as **published secrets**: never reuse them anywhere real, and
  rotate them before exposing any environment built from this tree.
- The real `.env` file is gitignored and never committed.

## Lab network posture

- `infrastructure/k8s/kind-config.yaml` binds QA ports to `127.0.0.1` only.
  The lab must never listen on LAN/WAN interfaces.
- The QA deployment intentionally runs the **`local` Spring profile** with
  in-memory users (`admin` / `operator`) and HTTP Basic. There is no rate
  limiting or account lockout on Basic auth: do not expose port 8080.

## Production profile (demonstrated, not deployed here)

`backend/src/main/resources/application-prod.yml` binds all secrets with **no
defaults** (`${APP_ADMIN_PASSWORD}` etc.: missing env var fails startup at
placeholder resolution), and `CredentialHardeningCheck` refuses startup on a
non-local profile with a missing, short (<12 chars) or default (`changeme`)
password. No environment in this repo currently runs the `prod` profile; the
QA cluster runs `local` by design (see above).

## Least privilege (enforced in the lab)

- `APP_RUNTIME`: DML only via definer's-rights packages + `idempotency_key`;
  no `EXEMPT ACCESS POLICY`, no `DELETE` on business tables (asserted by
  `tests/security/T_security_tenant_isolation.sql`).
- `APP_REPORTING`: read-only (`SELECT` + tenant/VPD + refresh procedure).
- `APP_OWNER`: schema owner, holds `EXEMPT ACCESS POLICY` solely so the
  definer's-rights `refresh_reporting_views` can refresh MVs built on
  VPD-protected tables (ORA-30372 otherwise).

## Reporting a vulnerability

This is a personal experiment with no SLA. If you find something that would
put lab users at risk, please open a GitHub issue with the `security` label
and avoid publishing exploit details until it is addressed.
