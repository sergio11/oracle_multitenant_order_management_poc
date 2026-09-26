# ADR-006 — MV Reporting + Freshness + Rate Limit

Status: Accepted

## Context
Analytical queries must not load OLTP tables (SRS §38-43); unbounded
`DBMS_MVIEW.REFRESH` calls risk OLTP impact.

## Decision
`MV_ORDER_DAILY_SUMMARY` + `MV_PRODUCT_SALES_SUMMARY`, COMPLETE REFRESH ON
DEMAND (justified: low POC volume; query rewrite deliberately OFF).
`RateLimiterPort` + in-memory limiter, cooldown
`app.reporting.refresh-cooldown-seconds` (default 60), refund on failure,
`@Transactional` refresh; `limit` `@Min(1)/@Max(100)` + service guard.
New `GET /api/v1/reports/freshness` + `X-MV-Last-Refresh` header on report
GETs (best-effort, never fails the read).

## Consequences
+ OLTP/reporting separation; clients can observe staleness. − COMPLETE
refresh cost grows with volume; limiter is per-JVM (multi-node gap).
