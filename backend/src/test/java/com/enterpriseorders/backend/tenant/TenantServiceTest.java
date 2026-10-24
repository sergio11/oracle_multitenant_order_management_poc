package com.enterpriseorders.backend.tenant;

import com.enterpriseorders.backend.exception.BusinessException;
import com.enterpriseorders.backend.tenant.application.TenantService;
import com.enterpriseorders.backend.web.error.GlobalExceptionHandler;import com.enterpriseorders.backend.tenant.model.NewTenantBO;
import com.enterpriseorders.backend.tenant.model.TenantBO;
import com.enterpriseorders.backend.tenant.port.output.DedicatedTenantProvisioner;
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
    @Mock private DedicatedTenantProvisioner dedicatedTenantProvisioner;
    @InjectMocks private TenantService tenantService;

    @Test
    void shouldCreateTenant() {
        var request = new NewTenantBO("ACME", "Acme Corp", "STANDARD", "SHARED_SCHEMA");
        when(tenantRepository.create(request)).thenReturn(1L);
        when(tenantRepository.findById(1L)).thenReturn(new TenantBO(1L, "ACME", "Acme Corp", "STANDARD", "ACTIVE", "SHARED_SCHEMA"));
        when(dedicatedTenantProvisioner.ensureProvisioned(request)).thenReturn(1L);
        TenantBO result = tenantService.createTenant(request);
        assertEquals(1L, result.tenantId());
        assertEquals("ACME", result.tenantCode());
        verify(tenantRepository).create(request);
        verify(tenantRepository).findById(1L);
        // Both PDBs must hold the row under the SAME identity id.
        verify(dedicatedTenantProvisioner).ensureProvisioned(request);
    }

    @Test
    void shouldFailClosedWhenDedicatedProvisioningDisagrees() {
        var request = new NewTenantBO("ACME", "Acme Corp", "ENTERPRISE", "SHARED_SCHEMA");
        when(tenantRepository.create(request)).thenReturn(1L);
        when(dedicatedTenantProvisioner.ensureProvisioned(request)).thenReturn(99L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> tenantService.createTenant(request));

        assertEquals("TENANT_PROVISION_CONFLICT", ex.getCode());
        // 500: an id mismatch is an environment defect, never a client error.
        assertEquals(500, GlobalExceptionHandler.statusFor(ex.getCode()).value());
        verify(tenantRepository, never()).findById(anyLong());
    }

    @Test
    void shouldPropagateMirrorFailureInsteadOfReturningAHalfCreatedTenant() {
        var request = new NewTenantBO("ACME", "Acme Corp", "STANDARD", "SHARED_SCHEMA");
        when(tenantRepository.create(request)).thenReturn(1L);
        when(dedicatedTenantProvisioner.ensureProvisioned(request))
                .thenThrow(new RuntimeException("dedicated PDB unavailable"));

        assertThrows(RuntimeException.class, () -> tenantService.createTenant(request));
        verify(tenantRepository, never()).findById(anyLong());
    }

    @Test
    void shouldGetTenant() {
        when(tenantRepository.findById(1L)).thenReturn(new TenantBO(1L, "ACME", "Acme Corp", "STANDARD", "ACTIVE", "SHARED_SCHEMA"));
        TenantBO result = tenantService.getTenant(1L);
        assertEquals("ACME", result.tenantCode());
        verify(tenantRepository).findById(1L);
    }

    @Test
    void shouldResolveActiveTenantWithTierFromDatabase() {
        when(tenantRepository.findActiveByCode("INITECH"))
                .thenReturn(Optional.of(new TenantBO(3L, "INITECH", "Initech",
                        "ENTERPRISE", "ACTIVE", "SHARED_SCHEMA")));
        TenantBO resolved = tenantService.resolveActiveTenant("INITECH");
        assertEquals(3L, resolved.tenantId());
        // The tier must be whatever the row says: routing cannot infer it
        // from the tenant code.
        assertEquals("ENTERPRISE", resolved.tier());
    }
    @Test
    void shouldThrowTenantAccessDeniedWhenResolvingUnknownCode() {

        when(tenantRepository.findActiveByCode("UNKNOWN")).thenReturn(Optional.empty());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> tenantService.resolveActiveTenant("UNKNOWN"));
        // Anti-enumeration: the resolution path must not reveal whether a
        // code exists (401), unlike the persistence 404 for absent records.
        assertEquals("TENANT_ACCESS_DENIED", ex.getCode());
        assertEquals(401, GlobalExceptionHandler.statusFor(ex.getCode()).value());
    }

    @Test
    void shouldNormalizeTenantCodeToUppercaseOnCreate() {
        // Closure fix: resolution is case-insensitive (filter uppercases),
        // so a verbatim lowercase code would provision an unreachable
        // tenant. NewTenantBO normalizes at the application boundary.
        var request = new NewTenantBO(" acme ", "Acme Corp", "STANDARD", "SHARED_SCHEMA");
        assertEquals("ACME", request.tenantCode());

        when(tenantRepository.create(request)).thenReturn(1L);
        when(tenantRepository.findById(1L)).thenReturn(new TenantBO(1L, "ACME", "Acme Corp", "STANDARD", "ACTIVE", "SHARED_SCHEMA"));
        when(dedicatedTenantProvisioner.ensureProvisioned(request)).thenReturn(1L);
        tenantService.createTenant(request);

        var captor = org.mockito.ArgumentCaptor.forClass(NewTenantBO.class);
        verify(tenantRepository).create(captor.capture());
        assertEquals("ACME", captor.getValue().tenantCode());
    }
}
