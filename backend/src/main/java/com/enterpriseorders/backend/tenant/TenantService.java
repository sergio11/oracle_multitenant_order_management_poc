package com.enterpriseorders.backend.tenant;

import com.enterpriseorders.backend.tenant.dto.TenantRequest;
import com.enterpriseorders.backend.tenant.dto.TenantResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantService {

    private final TenantDao tenantDao;

    public TenantService(TenantDao tenantDao) {
        this.tenantDao = tenantDao;
    }

    @Transactional
    public TenantResponse createTenant(TenantRequest request) {
        Long tenantId = tenantDao.createTenant(request);
        return tenantDao.findTenant(tenantId);
    }

    @Transactional(readOnly = true)
    public TenantResponse getTenant(Long tenantId) {
        return tenantDao.findTenant(tenantId);
    }
}
