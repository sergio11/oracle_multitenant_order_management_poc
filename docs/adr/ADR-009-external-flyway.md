# ADR-009 — External Flyway Bootstrap (spring.flyway Disabled)

Status: Accepted

## Context
Runtime Flyway couples app startup to DDL privileges and complicates
least-privilege (`APP_RUNTIME` must not own DDL).

## Decision
`spring.flyway.enabled=false` in app config; migrations (V001–V022) run via
Maven Flyway plugin / rake (`qa:db:migrate`); PL/SQL bodies via
`deploy_packages.sql`. Prod profile keeps Flyway enabled flag only for
controlled environments (external bootstrap remains the norm).

## Consequences
+ Runtime user stays least-privilege; explicit migration step. − Extra
deploy step; packages duplicated (V010 vs `database/packages/`) and synced
manually.
