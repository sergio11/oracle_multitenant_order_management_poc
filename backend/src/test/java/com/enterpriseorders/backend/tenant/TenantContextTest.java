package com.enterpriseorders.backend.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TenantContextTest {
    private final TenantContext tenantContext = new TenantContext();

    @AfterEach
    void tearDown() { tenantContext.clear(); }

    @Test
    void shouldReturnNullWhenNotSet() { assertNull(tenantContext.getTenantId()); }

    @Test
    void shouldSetAndGetTenantId() {
        tenantContext.setTenantId(42L);
        assertEquals(42L, tenantContext.getTenantId());
    }

    @Test
    void shouldClearTenantId() {
        tenantContext.setTenantId(42L);
        tenantContext.clear();
        assertNull(tenantContext.getTenantId());
    }
}
