package com.enterpriseorders.backend.tenant.dto;

/**
 * Response payload for tenant operations (SRS §53).
 */
public record TenantResponse(
        Long tenantId,
        String tenantCode,
        String name,
        String tier,
        String status,
        String isolationMode
) {
}
</content>
<task_progress>
- [x] FASE 6: infraestructura backend base + TenantAwareDataSource + filtros + TenantContext/Dao + TenantRequest
- [x] FASE 6: TenantResponse DTO
- [ ] FASE 6: TenantService
- [ ] FASE 6: TenantController
- [ ] FASE 6: application-test.yml
- [ ] FASE 6: Commit fase 6
- [ ] FASE 7 a 12 (resto del plan)
</task_progress>