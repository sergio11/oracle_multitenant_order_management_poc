package com.enterpriseorders.backend.tenant.application;

import com.enterpriseorders.backend.exception.BusinessException;
import com.enterpriseorders.backend.tenant.model.NewTenantBO;
import com.enterpriseorders.backend.tenant.model.TenantBO;
import com.enterpriseorders.backend.tenant.port.input.CreateTenantUseCase;
import com.enterpriseorders.backend.tenant.port.input.GetTenantQuery;
import com.enterpriseorders.backend.tenant.port.input.ResolveTenantUseCase;
import com.enterpriseorders.backend.tenant.port.output.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantService implements CreateTenantUseCase, GetTenantQuery, ResolveTenantUseCase {

    private final TenantRepository tenantRepository;

    public TenantService(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Transactional
    public TenantBO createTenant(NewTenantBO request) {
        Long tenantId = tenantRepository.create(request);
        return tenantRepository.findById(tenantId);
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
    public long resolveActiveTenantId(String tenantCode) {
        return tenantRepository.findActiveTenantIdByCode(tenantCode)
                .orElseThrow(() -> new BusinessException("TENANT_ACCESS_DENIED",
                        "Unknown or inactive tenant"));
    }
}
