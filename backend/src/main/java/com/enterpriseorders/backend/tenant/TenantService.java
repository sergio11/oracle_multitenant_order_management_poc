package com.enterpriseorders.backend.tenant;

import com.enterpriseorders.backend.tenant.dto.TenantRequest;
import com.enterpriseorders.backend.tenant.dto.TenantResponse;
import com.enterpriseorders.backend.tenant.port.input.CreateTenantUseCase;
import com.enterpriseorders.backend.tenant.port.input.GetTenantQuery;
import com.enterpriseorders.backend.tenant.port.output.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantService implements CreateTenantUseCase, GetTenantQuery {

    private final TenantRepository tenantRepository;

    public TenantService(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Transactional
    public TenantResponse createTenant(TenantRequest request) {
        Long tenantId = tenantRepository.create(request);
        return tenantRepository.findById(tenantId);
    }

    @Transactional(readOnly = true)
    public TenantResponse getTenant(Long tenantId) {
        return tenantRepository.findById(tenantId);
    }
}
