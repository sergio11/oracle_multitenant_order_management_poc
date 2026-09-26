package com.enterpriseorders.backend.tenant.model;

/**
 * Neutral read model for tenants (A3).
 *
 * <p>Mirrors the web-facing {@code dto.TenantResponse} field-for-field.
 * Input/output ports, the application service and the persistence adapter
 * depend on this type, never on web DTOs. The controller maps
 * {@code TenantView} to {@code TenantResponse} at the edge.
 */
public record TenantView(
        Long tenantId,
        String tenantCode,
        String name,
        String tier,
        String status,
        String isolationMode
) {
}
