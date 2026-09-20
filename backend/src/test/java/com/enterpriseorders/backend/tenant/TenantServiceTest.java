package com.enterpriseorders.backend.tenant;

import com.enterpriseorders.backend.tenant.dto.TenantRequest;
import com.enterpriseorders.backend.tenant.dto.TenantResponse;
import com.enterpriseorders.backend.tenant.port.output.TenantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {
    @Mock private TenantRepository tenantRepository;
    @InjectMocks private TenantService tenantService;

    @Test
    void shouldCreateTenant() {
        var request = new TenantRequest("ACME", "Acme Corp", "STANDARD", null);
        when(tenantRepository.create(request)).thenReturn(1L);
        when(tenantRepository.findById(1L)).thenReturn(new TenantResponse(1L, "ACME", "Acme Corp", "STANDARD", "ACTIVE", "SHARED_SCHEMA"));
        TenantResponse result = tenantService.createTenant(request);
        assertEquals(1L, result.tenantId());
        assertEquals("ACME", result.tenantCode());
        verify(tenantRepository).create(request);
        verify(tenantRepository).findById(1L);
    }

    @Test
    void shouldGetTenant() {
        when(tenantRepository.findById(1L)).thenReturn(new TenantResponse(1L, "ACME", "Acme Corp", "STANDARD", "ACTIVE", "SHARED_SCHEMA"));
        TenantResponse result = tenantService.getTenant(1L);
        assertEquals("ACME", result.tenantCode());
        verify(tenantRepository).findById(1L);
    }
}
