package com.enterpriseorders.backend.tenant.dto;

import com.enterpriseorders.backend.tenant.model.TenantView;

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

    /** Maps the neutral port model to the web-facing DTO at the edge. */
    public static TenantResponse from(TenantView view) {
        return new TenantResponse(
                view.tenantId(),
                view.tenantCode(),
                view.name(),
                view.tier(),
                view.status(),
                view.isolationMode());
    }
}
