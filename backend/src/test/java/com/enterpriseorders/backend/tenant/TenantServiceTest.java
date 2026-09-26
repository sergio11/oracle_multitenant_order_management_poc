package com.enterpriseorders.backend.tenant;

import com.enterpriseorders.backend.exception.BusinessException;
import com.enterpriseorders.backend.tenant.application.TenantService;
import com.enterpriseorders.backend.tenant.model.NewTenant;
import com.enterpriseorders.backend.tenant.model.TenantView;
import com.enterpriseorders.backend.tenant.port.output.TenantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {
    @Mock private TenantRepository tenantRepository;
    @InjectMocks private TenantService tenantService;

    @Test
    void shouldCreateTenant() {
        var request = new NewTenant("ACME", "Acme Corp", "STANDARD", "SHARED_SCHEMA");
        when(tenantRepository.create(request)).thenReturn(1L);
        when(tenantRepository.findById(1L)).thenReturn(new TenantView(1L, "ACME", "Acme Corp", "STANDARD", "ACTIVE", "SHARED_SCHEMA"));
        TenantView result = tenantService.createTenant(request);
        assertEquals(1L, result.tenantId());
        assertEquals("ACME", result.tenantCode());
        verify(tenantRepository).create(request);
        verify(tenantRepository).findById(1L);
    }

    @Test
    void shouldGetTenant() {
        when(tenantRepository.findById(1L)).thenReturn(new TenantView(1L, "ACME", "Acme Corp", "STANDARD", "ACTIVE", "SHARED_SCHEMA"));
        TenantView result = tenantService.getTenant(1L);
        assertEquals("ACME", result.tenantCode());
        verify(tenantRepository).findById(1L);
    }

    @Test
    void shouldResolveActiveTenantId() {
        when(tenantRepository.findActiveTenantIdByCode("ACME")).thenReturn(Optional.of(1L));
        assertEquals(1L, tenantService.resolveActiveTenantId("ACME"));
    }

    @Test
    void shouldThrowTenantNotFoundWhenResolvingUnknownCode() {
        when(tenantRepository.findActiveTenantIdByCode("UNKNOWN")).thenReturn(Optional.empty());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> tenantService.resolveActiveTenantId("UNKNOWN"));
        assertEquals("TENANT_NOT_FOUND", ex.getCode());
        assertEquals(401, ex.getHttpStatus());
    }
}
