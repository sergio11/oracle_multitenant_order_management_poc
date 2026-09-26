package com.enterpriseorders.backend.config;

import com.enterpriseorders.backend.tenant.TenantContext;
import com.enterpriseorders.backend.tenant.port.output.TenantContextProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantAwareDataSourceTest {

    @Mock
    private DataSource delegateDataSource;

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement preparedStatement;

    private TenantContext tenantContext;
    private TenantAwareDataSource tenantAwareDataSource;

    @BeforeEach
    void setUp() throws SQLException {
        tenantContext = new TenantContext();
        tenantAwareDataSource = new TenantAwareDataSource(delegateDataSource, tenantContext, "APP_OWNER");
        lenient().when(delegateDataSource.getConnection()).thenReturn(connection);
        lenient().when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
    }

    @Test
    void shouldApplyTenantContextOnGetConnection() throws SQLException {
        tenantContext.setTenantId(1L);

        Connection result = tenantAwareDataSource.getConnection();

        assertNotNull(result);
        verify(connection).prepareStatement(contains("set_tenant_context"));
    }

    @Test
    void shouldNotApplyContextWhenTenantIsNull() throws SQLException {
        Connection result = tenantAwareDataSource.getConnection();

        assertNotNull(result);
        verify(connection, never()).prepareStatement(contains("set_tenant_context"));
    }

    @Test
    void shouldDelegateIsWrapperFor() throws SQLException {
        when(delegateDataSource.isWrapperFor(String.class)).thenReturn(true);

        assertTrue(tenantAwareDataSource.isWrapperFor(String.class));
        verify(delegateDataSource).isWrapperFor(String.class);
    }

    @Test
    void shouldDelegateGetLoginTimeout() throws SQLException {
        when(delegateDataSource.getLoginTimeout()).thenReturn(30);

        assertEquals(30, tenantAwareDataSource.getLoginTimeout());
    }

    @Test
    void shouldReturnProxiedConnection() throws SQLException {
        tenantContext.setTenantId(1L);

        Connection result = tenantAwareDataSource.getConnection();

        assertNotNull(result);
        assertTrue(Proxy.isProxyClass(result.getClass()));
    }

    @Test
    void shouldClearTenantContextOnClose() throws SQLException {
        tenantContext.setTenantId(1L);
        when(connection.isClosed()).thenReturn(false);

        Connection proxied = tenantAwareDataSource.getConnection();
        proxied.close();

        verify(connection).prepareStatement(contains("clear_tenant_context"));
    }

    @Test
    void shouldNotClearContextIfConnectionAlreadyClosed() throws SQLException {
        tenantContext.setTenantId(1L);
        when(connection.isClosed()).thenReturn(true);

        Connection proxied = tenantAwareDataSource.getConnection();
        proxied.close();

        verify(connection, never()).prepareStatement(contains("clear_tenant_context"));
    }

    @Test
    void shouldDelegateGetConnectionWithCredentials() throws SQLException {
        Connection credConnection = mock(Connection.class);
        when(delegateDataSource.getConnection("user", "pass")).thenReturn(credConnection);
        when(credConnection.prepareStatement(anyString())).thenReturn(preparedStatement);
        tenantContext.setTenantId(1L);

        Connection result = tenantAwareDataSource.getConnection("user", "pass");

        assertNotNull(result);
        verify(delegateDataSource).getConnection("user", "pass");
    }

    @Test
    void shouldDelegateSetLoginTimeout() throws SQLException {
        tenantAwareDataSource.setLoginTimeout(60);
        verify(delegateDataSource).setLoginTimeout(60);
    }

    @Test
    void shouldDelegateUnwrap() throws SQLException {
        String expected = "unwrapped";
        when(delegateDataSource.unwrap(String.class)).thenReturn(expected);

        String result = tenantAwareDataSource.unwrap(String.class);

        assertEquals(expected, result);
    }

    // ---- H1: no connection leak when session setup fails ----

    @Test
    void shouldCloseConnectionWhenSessionSetupFails() throws SQLException {
        when(connection.prepareStatement(anyString())).thenThrow(new SQLException("session setup failed"));

        SQLException thrown = assertThrows(SQLException.class, () -> tenantAwareDataSource.getConnection());

        assertEquals("session setup failed", thrown.getMessage());
        verify(connection).close();
    }

    @Test
    void shouldCloseConnectionWhenTenantLookupFails() throws SQLException {
        TenantContextProvider failing = () -> {
            throw new IllegalStateException("no tenant available");
        };
        TenantAwareDataSource ds = new TenantAwareDataSource(delegateDataSource, failing, "APP_OWNER");

        assertThrows(IllegalStateException.class, ds::getConnection);

        verify(connection).close();
    }

    @Test
    void shouldSuppressCloseFailureAndRethrowOriginalError() throws SQLException {
        when(connection.prepareStatement(anyString())).thenThrow(new SQLException("setup failed"));
        doThrow(new SQLException("pool closed")).when(connection).close();

        SQLException thrown = assertThrows(SQLException.class, () -> tenantAwareDataSource.getConnection());

        assertEquals("setup failed", thrown.getMessage());
        assertEquals(1, thrown.getSuppressed().length);
        assertEquals("pool closed", thrown.getSuppressed()[0].getMessage());
    }

    // ---- M10: round-trip reduction ----

    @Test
    void shouldApplySchemaAndTenantContextInSingleRoundTrip() throws SQLException {
        tenantContext.setTenantId(1L);

        tenantAwareDataSource.getConnection();

        verify(connection, times(1)).prepareStatement(anyString());
        verify(connection).prepareStatement(contains("CURRENT_SCHEMA"));
        verify(connection).prepareStatement(contains("set_tenant_context"));
    }

    @Test
    void shouldApplySchemaAndClearInSingleRoundTripWhenTenantNull() throws SQLException {
        tenantAwareDataSource.getConnection();

        verify(connection, times(1)).prepareStatement(anyString());
        verify(connection).prepareStatement(contains("CURRENT_SCHEMA"));
        verify(connection).prepareStatement(contains("clear_tenant_context"));
        verify(connection, never()).prepareStatement(contains("set_tenant_context"));
    }

    @Test
    void shouldSkipCloseSideClearWhenNoTenantApplied() throws SQLException {
        Connection proxied = tenantAwareDataSource.getConnection();

        proxied.close();

        // only the borrow-side merged block (which embeds the clear) ran
        verify(connection, times(1)).prepareStatement(contains("clear_tenant_context"));
        verify(connection).close();
    }

    // ---- M4: clear failure on return ----

    @Test
    void shouldRetryClearOnCloseAndReturnConnection() throws SQLException {
        tenantContext.setTenantId(1L);
        when(connection.prepareStatement(anyString()))
                .thenReturn(preparedStatement)
                .thenThrow(new SQLException("clear failed"))
                .thenReturn(preparedStatement);

        Connection proxied = tenantAwareDataSource.getConnection();
        proxied.close();

        verify(connection, times(2)).prepareStatement(contains("clear_tenant_context"));
        verify(connection).close();
    }

    @Test
    void shouldReturnConnectionEvenWhenClearFailsTwice() throws SQLException {
        tenantContext.setTenantId(1L);
        when(connection.prepareStatement(anyString()))
                .thenReturn(preparedStatement)
                .thenThrow(new SQLException("clear failed 1"))
                .thenThrow(new SQLException("clear failed 2"));

        Connection proxied = tenantAwareDataSource.getConnection();

        assertDoesNotThrow(proxied::close);
        verify(connection, times(2)).prepareStatement(contains("clear_tenant_context"));
        verify(connection).close();
    }

    // ---- L3: schema validation (fail fast at startup) ----

    @Test
    void shouldRejectInvalidSchemaAtConstruction() {
        assertThrows(IllegalArgumentException.class,
                () -> new TenantAwareDataSource(delegateDataSource, tenantContext, "APP; DROP USER X"));
        assertThrows(IllegalArgumentException.class,
                () -> new TenantAwareDataSource(delegateDataSource, tenantContext, "app_owner"));
        assertThrows(IllegalArgumentException.class,
                () -> new TenantAwareDataSource(delegateDataSource, tenantContext, "1APP"));
        assertThrows(IllegalArgumentException.class,
                () -> new TenantAwareDataSource(delegateDataSource, tenantContext, null));
    }

    @Test
    void shouldAcceptValidSchemaAtConstruction() {
        assertDoesNotThrow(() -> new TenantAwareDataSource(delegateDataSource, tenantContext, "APP_OWNER"));
        assertDoesNotThrow(() -> new TenantAwareDataSource(delegateDataSource, tenantContext, "APP$2#"));
    }

    // ---- L7: proxy identity semantics ----

    @Test
    void shouldUseIdentityEqualsAndHashCodeOnProxy() throws SQLException {
        Connection proxied = tenantAwareDataSource.getConnection();
        Connection other = mock(Connection.class);

        assertTrue(proxied.equals(proxied));
        assertFalse(proxied.equals(other));
        assertEquals(System.identityHashCode(proxied), proxied.hashCode());
    }

    @Test
    void shouldUnwrapConnectionInterfaceToGuardedProxy() throws SQLException {
        Connection proxied = tenantAwareDataSource.getConnection();

        assertSame(proxied, proxied.unwrap(Connection.class));
        assertTrue(proxied.isWrapperFor(Connection.class));
    }

    @Test
    void shouldDelegateVendorUnwrapToRawConnection() throws SQLException {
        Connection proxied = tenantAwareDataSource.getConnection();
        when(connection.unwrap(String.class)).thenReturn("raw");

        assertEquals("raw", proxied.unwrap(String.class));
        verify(connection).unwrap(String.class);
    }
}
