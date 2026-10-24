package com.enterpriseorders.backend.tenant.port.output;

import com.enterpriseorders.backend.tenant.model.NewTenantBO;

/**
 * Output port that keeps the dedicated PDB (PDB_CORP) in step with the
 * shared PDB when a tenant is created.
 *
 * <p>Tenant ids are identity-generated, so the very same row must be
 * provisioned in both PDBs or the two sequences drift and an ENTERPRISE
 * tenant would resolve to an id that does not exist where it is served.
 * The mirror is therefore mandatory, not best effort: a failure here must
 * fail the tenant creation (fail closed) instead of leaving a tenant that
 * routes to a PDB that does not know it.
 *
 * <p>When the dedicated DataSource points at the same database as the shared
 * one (the {@code prod} default: {@code dedicated-url = url}), the port
 * degenerates to a read of the row that was just created - there is nothing
 * to mirror.
 */
public interface DedicatedTenantProvisioner {

    /**
     * @return the {@code tenant_id} of the tenant in the dedicated PDB
     * @throws com.enterpriseorders.backend.exception.BusinessException when
     *         the dedicated PDB holds the same code under a different id
     *         (code {@code TENANT_PROVISION_CONFLICT})
     */
    long ensureProvisioned(NewTenantBO request);
}
