package com.enterpriseorders.backend.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request payload for tenant provisioning (SRS FR-001, §53).
 */
public record TenantRequestDTO(
        @NotBlank @Size(max = 30) String tenantCode,
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Pattern(regexp = "STANDARD|PREMIUM|ENTERPRISE") String tier,
        @Pattern(regexp = "SHARED_SCHEMA|DEDICATED_SCHEMA|DEDICATED_PDB",
                 message = "isolationMode must be SHARED_SCHEMA, DEDICATED_SCHEMA or DEDICATED_PDB")
        String isolationMode
) {

    public TenantRequestDTO {
        if (isolationMode == null || isolationMode.isBlank()) {
            isolationMode = "SHARED_SCHEMA";
        }
    }
}
