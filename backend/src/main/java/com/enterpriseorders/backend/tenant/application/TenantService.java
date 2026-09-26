package com.enterpriseorders.backend.tenant.application;

import com.enterpriseorders.backend.exception.BusinessException;
import com.enterpriseorders.backend.tenant.model.NewTenant;
import com.enterpriseorders.backend.tenant.model.TenantView;
import com.enterpriseorders.backend.tenant.port.input.CreateTenantUseCase;
import com.enterpriseorders.backend.tenant.port.input.GetTenantQuery;
import com.enterpriseorders.backend.tenant.port.input.ResolveTenantUseCase;
import com.enterpriseorders.backend.tenant.port.output.TenantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantService implements CreateTenantUseCase, GetTenantQuery, ResolveTenantUseCase {

    private final TenantRepository tenantRepository;

    public TenantService(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Transactional
    public TenantView createTenant(NewTenant request) {
        Long tenantId = tenantRepository.create(request);
        return tenantRepository.findById(tenantId);
    }

    @Transactional(readOnly = true)
    public TenantView getTenant(Long tenantId) {
        return tenantRepository.findById(tenantId);
    }

    @Override
    @Transactional(readOnly = true)
    public long resolveActiveTenantId(String tenantCode) {
        return tenantRepository.findActiveTenantIdByCode(tenantCode)
                .orElseThrow(() -> new BusinessException("TENANT_NOT_FOUND",
                        "Unknown or inactive tenant", HttpStatus.UNAUTHORIZED));
    }
}
