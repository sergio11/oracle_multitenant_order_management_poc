# ADR-010 — Single-Block Session Init + Least-Privilege Users

Status: Accepted

## Context
3 round-trips per borrow (schema, context, verify) + over-granted users
(`app_reporting` could execute mutating packages; runtime held direct
audit/outbox DML).

## Decision
One anonymous block per borrow (schema+context together); schema
regex-validated `[A-Z_][A-Z0-9_$#]*` at construction. Grants: `app_reporting`
= SELECT + `pkg_tenant`/`pkg_vpd` + refresh proc only (V019 revokes
`pkg_order_management/pkg_inventory/pkg_audit`; V017 fixed for fresh
installs); runtime writes only via definer's-rights packages + direct
`idempotency_key` DML (V019 revokes direct audit/history/outbox DML);
`deploy_packages.sql`/`pkb` tails no longer grant mutating pkgs to reporting.

## Consequences
+ 1 round-trip/borrow; reporting truly read-only. − V019 needed for
already-migrated DBs (edit+V017 repair alone would not re-execute grants).
