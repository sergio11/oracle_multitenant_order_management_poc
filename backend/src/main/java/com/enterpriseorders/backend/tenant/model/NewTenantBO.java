package com.enterpriseorders.backend.tenant.model;

/**
 * Neutral write model for tenant provisioning (A3).
 *
 * <p>Mirrors the web-facing {@code dto.TenantRequestDTO} field-for-field
 * (validation stays on the DTO at the edge). The controller maps the
 * request DTO to this type before calling the input port.
 *
 * <p>Closure fix: {@code tenantCode} is normalized here (trim + upper
 * case), the single choke point every write path goes through. Tenant
 * resolution (filter, allowlist, {@code TenantDao}) is case-insensitive
 * by design, so persisting a code verbatim (e.g. {@code "acme"}) used
 * to create a tenant that could never be reached - uniqueness is
 * case-sensitive in Oracle while every read uppercases first.
 */
public record NewTenantBO(
        String tenantCode,
        String name,
        String tier,
        String isolationMode
) {
    public NewTenantBO {
        tenantCode = tenantCode == null ? null : tenantCode.trim().toUpperCase();
        // TIER is the single source of truth for physical isolation
        // (TenantRoutingDataSource routes on it). Derive ISOLATION_MODE
        // here so the DTO default can never persist a contradiction such
        // as tier=ENTERPRISE + isolation_mode=SHARED_SCHEMA.
        if ("ENTERPRISE".equalsIgnoreCase(tier)) {
            isolationMode = "DEDICATED_PDB";
        } else if (isolationMode == null || isolationMode.isBlank()) {
            isolationMode = "SHARED_SCHEMA";
        }
    }
}
