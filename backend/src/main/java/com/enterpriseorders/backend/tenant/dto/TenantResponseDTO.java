package com.enterpriseorders.backend.tenant.dto;

/**
 * Response payload for tenant operations (SRS §53).
 */
public record TenantResponseDTO(
        Long tenantId,
        String tenantCode,
        String name,
        String tier,
        String status,
        String isolationMode
) {
}
