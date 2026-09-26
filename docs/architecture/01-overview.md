# 01 — Overview

Components (current code):

```mermaid
flowchart LR
  WEB["adapter/web<br/>controllers, TenantContextFilter"]
  SHARED["web.* + config + execution<br/>CorrelationFilter, GlobalExceptionHandler, TenantAwareDataSource, ExecutionContext bridge"]
  IN["port/input<br/>use-case interfaces"]
  APP["application<br/>Order/Tenant/ReportingService, IdempotentOperationExecutor"]
  OUT["port/output<br/>repositories, RateLimiterPort, Reporting DTOs"]
  MODEL["model<br/>OrderView, TenantView/NewTenant"]
  PER["adapter/persistence<br/>DAOs"]
  DB[("Oracle 23c<br/>PL/SQL, VPD x16, MVs")]
  WEB --> IN --> APP --> OUT --> PER --> DB
  APP -.-> EXEC["execution"]
  MODEL -.-> IN & OUT
```

Enforcement: `architecture/HexagonalArchitectureTest` (ArchUnit, 15 rules).
Ports use model types; controllers map to DTOs (wire JSON unchanged).
Domain package deleted; `PaymentMethod` literal enum + consistency test only.
Reporting adds `ratelimiter/` and `GET /reports/freshness` + header.
