# ADR-004 — JDBC over JPA

Status: Accepted

## Context
SRS §50: POC must expose Oracle specifics (FOR UPDATE, PL/SQL calls,
VPD sessions, MVs). JPA would hide SQL and complicate session control.

## Decision
Spring JDBC + explicit `CallableStatement` via DAOs; no JPA/Hibernate.
`TenantAwareDataSource` owns per-borrow session init; every query visible
and auditable.

## Consequences
+ Full SQL/PLSQL control, no hidden queries. − More boilerplate;
no ORM productivity features (accepted tradeoff, documented in README).
