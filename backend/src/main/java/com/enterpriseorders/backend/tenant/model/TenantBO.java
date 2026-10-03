package com.enterpriseorders.backend.tenant.model;

/**
 * Neutral read model for tenants (A3).
 *
 * <p>Mirrors the web-facing {@code dto.TenantResponseDTO} field-for-field.
 * Input/output ports, the application service and the persistence adapter
 * depend on this type, never on web DTOs. The web edge maps
 * {@code TenantBO} to {@code TenantResponseDTO} with MapStruct
 * ({@code adapter.web.mapper.TenantMapper}).
 */
public record TenantBO(
        Long tenantId,
        String tenantCode,
        String name,
        String tier,
        String status,
        String isolationMode
) {
}
