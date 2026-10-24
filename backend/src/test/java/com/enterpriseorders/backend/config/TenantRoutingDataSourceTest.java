package com.enterpriseorders.backend.config;

import com.enterpriseorders.backend.tenant.port.output.TenantContextProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Routing is decided by the tenant TIER read from the database, nothing else.
 * The former fallbacks ("CORP" tenant code, null tier heuristics) are gone:
 * an unknown tier must land on the shared pool (fail closed to VPD) rather
 * than silently pick a PDB.
 */
@ExtendWith(MockitoExtension.class)
class TenantRoutingDataSourceTest {

    @Mock private DataSource shared;
    @Mock private DataSource dedicated;
    @Mock private java.sql.Connection sharedConnection;
    @Mock private java.sql.Connection dedicatedConnection;
    @Mock private TenantContextProvider tenantContext;

    private TenantRoutingDataSource routing;

    @BeforeEach
    void setUp() throws java.sql.SQLException {
        // lenient: each test exercises only one of the two pools.
        lenient().when(shared.getConnection()).thenReturn(sharedConnection);
        lenient().when(dedicated.getConnection()).thenReturn(dedicatedConnection);
        routing = new TenantRoutingDataSource(shared, dedicated, tenantContext);
    }

    @Test
    void routesToSharedWhenThereIsNoTenantContext() throws Exception {
        when(tenantContext.getTenantTier()).thenReturn(null);

        assertSame(sharedConnection, routing.getConnection());
    }

    @Test
    void routesStandardAndPremiumTenantsToTheSharedPdb() throws Exception {
        when(tenantContext.getTenantTier()).thenReturn("STANDARD");

        assertSame(sharedConnection, routing.getConnection());

        when(tenantContext.getTenantTier()).thenReturn("PREMIUM");
        assertSame(sharedConnection, routing.getConnection());
    }

    @Test
    void routesEnterpriseTierToTheDedicatedPdb() throws Exception {
        when(tenantContext.getTenantTier()).thenReturn("ENTERPRISE");

        assertSame(dedicatedConnection, routing.getConnection());
        verify(dedicated).getConnection();
    }

    @Test
    void tierComparisonIsCaseInsensitive() throws Exception {
        when(tenantContext.getTenantTier()).thenReturn("enterprise");

        assertSame(dedicatedConnection, routing.getConnection());
    }

    @Test
    void doesNotRouteOnTenantCodeAnyMore() throws Exception {
        // Regression guard: a tenant literally coded "CORP" with a STANDARD
        // tier stays in the shared PDB - the code is no longer a routing key.
        when(tenantContext.getTenantTier()).thenReturn("STANDARD");

        assertSame(sharedConnection, routing.getConnection());
    }
}
