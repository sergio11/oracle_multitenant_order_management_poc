package com.enterpriseorders.backend.config;

import com.enterpriseorders.backend.tenant.TenantContext;
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
        tenantAwareDataSource = new TenantAwareDataSource(delegateDataSource, tenantContext);
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
}
