package com.enterpriseorders.backend.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request payload for tenant provisioning (SRS FR-001, §53).
 */
public record TenantRequest(
        @NotBlank @Size(max = 30) String tenantCode,
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Pattern(regexp = "STANDARD|PREMIUM|ENTERPRISE") String tier,
        @Pattern(regexp = "SHARED_SCHEMA|DEDICATED_SCHEMA|DEDICATED_PDB",
                 message = "isolationMode must be SHARED_SCHEMA, DEDICATED_SCHEMA or DEDICATED_PDB")
        String isolationMode
) {

    public TenantRequest {
        if (isolationMode == null || isolationMode.isBlank()) {
            isolationMode = "SHARED_SCHEMA";
        }
    }
}
</content>
<task_progress>
- [x] FASE 1-5 completadas (f47859d, dded060, d3bd043, 3728f65, 8af9fcb)
- [x] FASE 6: infraestructura backend base + TenantAwareDataSource + filtros + TenantContext/Dao
- [x] FASE 6: TenantRequest DTO
- [ ] FASE 6: TenantResponse DTO
- [ ] FASE 6: TenantService
- [ ] FASE 6: TenantController
- [ ] FASE 6: application-test.yml
- [ ] FASE 6: Commit fase 6
- [ ] FASE 7 a 12 (resto del plan)
</task_progress>