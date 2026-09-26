# ADR-001 — Hexagonal Ports-Adapters Layout (+ ArchUnit)

Status: Accepted

## Context
Modules (order, tenant, reporting) mixed web/persistence/service types;
dependency direction was conventional, not enforced.

## Decision
Per-module layout: `adapter/web|persistence|security`, `application`,
`port/input|output`, `dto` (web edge), `model` (neutral: `OrderView`;
tenant `TenantView`/`NewTenant`; reporting port-local `port/output/dto`).
New `execution/` package (`ExecutionContext` port) for correlation/actor.
Enforce with `architecture/HexagonalArchitectureTest` (ArchUnit, ~11 rules:
web never touches persistence/application impls, application never touches
adapters/container APIs/MDC directly).

## Consequences
+ Swappable adapters; ports carry model types, controllers map to DTOs
  (wire JSON unchanged). − More packages; ArchUnit must stay green.
