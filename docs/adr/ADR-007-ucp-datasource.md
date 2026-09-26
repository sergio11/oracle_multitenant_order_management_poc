# ADR-007 — UCP + TenantAwareDataSource Connection Lifecycle

Status: Accepted

## Context
Pooled connections are shared across tenants; stale `APP_CTX` leaks rows
across tenants. SRS §51 demands Oracle-integrated pooling.

## Decision
Oracle UCP pool wrapped by `TenantAwareDataSource`: single anonymous block
per borrow (schema via `EXECUTE IMMEDIATE` + set/clear context = 1
round-trip); close-on-setup-failure (no leak); close-side clear skipped when
no tenant applied; proxy fixes equals/hashCode/unwrap. Config:
`abandoned-connection-timeout: 60`, `connection-validation-timeout: 10`,
validate-on-borrow.

## Consequences
+ Per-borrow normalized sessions; bounded stale-context residual.
− Vendor unwrap bypasses close-clear (documented); UCP has no portable
invalidate (timeout is the backstop).
