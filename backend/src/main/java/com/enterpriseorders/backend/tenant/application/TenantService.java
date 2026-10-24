package com.enterpriseorders.backend.tenant.application;

import com.enterpriseorders.backend.exception.BusinessException;
import com.enterpriseorders.backend.tenant.model.NewTenantBO;
import com.enterpriseorders.backend.tenant.model.TenantBO;
import com.enterpriseorders.backend.tenant.port.input.CreateTenantUseCase;
import com.enterpriseorders.backend.tenant.port.input.GetTenantQuery;
import com.enterpriseorders.backend.tenant.port.input.ResolveTenantUseCase;
import com.enterpriseorders.backend.tenant.port.output.DedicatedTenantProvisioner;
import com.enterpriseorders.backend.tenant.port.output.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantService implements CreateTenantUseCase, GetTenantQuery, ResolveTenantUseCase {

    private final TenantRepository tenantRepository;
    private final DedicatedTenantProvisioner dedicatedTenantProvisioner;

    public TenantService(TenantRepository tenantRepository,
                         DedicatedTenantProvisioner dedicatedTenantProvisioner) {
        this.tenantRepository = tenantRepository;
        this.dedicatedTenantProvisioner = dedicatedTenantProvisioner;
    }

    /**
     * Creates the tenant in the shared PDB and mirrors it into the dedicated
     * PDB so that identity-generated {@code tenant_id} values stay identical
     * in both (the shared PDB resolves the tenant, the dedicated PDB serves
     * it under that same id).
     *
     * <p>Deliberately NOT {@code @Transactional}: the mirror runs on a second
     * DataSource. Wrapping both in one transaction would either be invisible
     * to the dedicated pool or - when both URLs point at the same database -
     * block on the uncommitted unique index of the row being inserted. Each
     * step is a single statement, so statement-level atomicity is enough, and
     * a mirror failure surfaces as a 5xx instead of a silently half-created
     * tenant (fail closed).
     */
    public TenantBO createTenant(NewTenantBO request) {
        Long tenantId;
        try {
            tenantId = tenantRepository.create(request);
        } catch (BusinessException ex) {
            if (!"TENANT_ALREADY_EXISTS".equals(ex.getCode())) {
                throw ex;
            }
            // Duplicate POST still repairs the mirror: an earlier attempt may
            // have committed the shared row and failed before mirroring it.
            TenantBO existing = tenantRepository.findActiveByCode(request.tenantCode())
                    .orElseThrow(() -> ex);
            requireSameId(existing.tenantCode(), existing.tenantId(),
                    dedicatedTenantProvisioner.ensureProvisioned(request));
            throw ex;
        }
        requireSameId(request.tenantCode(), tenantId,
                dedicatedTenantProvisioner.ensureProvisioned(request));
        return tenantRepository.findById(tenantId);
    }

    private void requireSameId(String tenantCode, Long sharedId, long dedicatedId) {
        if (dedicatedId != sharedId) {
            throw new BusinessException("TENANT_PROVISION_CONFLICT",
                    "Tenant " + tenantCode + " has id " + sharedId + " in the shared PDB but "
                            + dedicatedId + " in the dedicated PDB; re-run rake qa:db:init");
        }
    }

    @Transactional(readOnly = true)
    public TenantBO getTenant(Long tenantId) {
        return tenantRepository.findById(tenantId);
    }

    /**
     * Resolves a tenant code to its id. Unknown or inactive codes raise
     * {@code TENANT_ACCESS_DENIED} (401, deliberate anti-enumeration: the
     * resolution path must not reveal whether a code exists), while the
     * persistence adapter reports a genuinely absent record as
     * {@code TENANT_NOT_FOUND} (404). See {@code GlobalExceptionHandler}.
     */
    @Override
    @Transactional(readOnly = true)
    public TenantBO resolveActiveTenant(String tenantCode) {
        return tenantRepository.findActiveByCode(tenantCode)
                .orElseThrow(() -> new BusinessException("TENANT_ACCESS_DENIED",
                        "Unknown or inactive tenant"));
    }
}
