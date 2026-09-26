package com.enterpriseorders.backend.tenant.model;

/**
 * Neutral write model for tenant provisioning (A3).
 *
 * <p>Mirrors the web-facing {@code dto.TenantRequest} field-for-field
 * (validation stays on the DTO at the edge). The controller maps the
 * request DTO to this type before calling the input port.
 */
public record NewTenant(
        String tenantCode,
        String name,
        String tier,
        String isolationMode
) {
}
