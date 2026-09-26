# 06 — Observability

- Correlation: `X-Correlation-ID` validated, generated when missing/invalid;
  MDC-first in logs and error bodies; propagated HTTP→Spring→Oracle→audit→outbox.
- Structured logs: JSON console pattern (timestamp, level, service, thread,
  correlationId, tenantId, logger, message).
- Actuator under `/internal` (health/info/metrics/prometheus); liveness vs
  readiness split (readiness includes `db`).
- Metrics: Micrometer/Prometheus (`/internal/prometheus`, authenticated);
  pool timeouts surface as controlled 503 (FT-001).
- Audit/outbox: `audit_event` (autonomous `pkg_audit`) records transitions
  with actor+correlation; `outbox_event` written atomically (publisher out of
  scope, rows stay NEW). ArchUnit + SecurityMatrix + DAO tests guard behavior.
