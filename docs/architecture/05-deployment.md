# 05 — Deployment

- Compose lab: `infrastructure/podman/compose.yaml` (gvenzl/oracle-free:23-slim,
  Oracle bound `127.0.0.1:1521`, `.env` secrets, first-boot `01-init-db.sql`);
  backend runs on host (`mvn spring-boot:run`) or image.
- kind QA: `rake qa:cluster:create → qa:image:build → qa:image:load →
  qa:deploy → qa:db:init (migrate V001–V022 + packages + seed) → qa:wait →
  qa:smoke` (8 checks, real ACME-vs-GLOBEX isolation).
- Image: multi-stage Dockerfile, `HEALTHCHECK` with start-period, OCI labels;
  non-root `65532`, `readOnlyRootFilesystem`+`/tmp` emptyDir, seccomp,
  no priv-esc; `terminationGracePeriodSeconds: 30`; startupProbe.
- Probes: liveness app-only, readiness includes `db` (DB outage → Unready,
  no restart). Management base-path `/internal` (no `/actuator/**`).
- NetworkPolicy file exists but is inert on kindnet (documented; needs a
  policy-enforcing CNI to take effect).
