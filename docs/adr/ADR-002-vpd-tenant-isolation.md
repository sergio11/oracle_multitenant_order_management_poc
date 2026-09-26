# ADR-002 — VPD/RLS Tenant Isolation (Fail-Closed)

Status: Accepted

## Context
Application-level `WHERE tenant_id=...` is bypassable by bug or injection;
SRS §13/#52 demand defense in depth.

## Decision
Oracle VPD/`DBMS_RLS` on 16 objects (13 base tables V011 + idempotency V015
+ 2 MVs V014); predicate `SYS_CONTEXT('APP_CTX','TENANT_ID')=tenant_id`,
fail-closed (`1=0`, zero rows) when context missing. `TENANT` table has no
policy (needed for resolution). Verified by
`database/security/verify_vpd_policies.sql` (16 policies, fail-loud).

## Consequences
+ Even SQL injection cannot leak cross-tenant rows. − MV build needs
policy drop/re-add window (V013 exception-safe block); session context must
be set per borrow (see ADR-007/010).
