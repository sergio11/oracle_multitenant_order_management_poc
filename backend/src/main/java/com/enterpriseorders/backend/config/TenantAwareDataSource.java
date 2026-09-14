package com.enterpriseorders.backend.config;

import com.enterpriseorders.backend.tenant.TenantContext;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

/**
 * Tenant-aware DataSource wrapper (SRS §52).
 *
 * Wraps the shared Oracle UCP pool and, per request, sets the Oracle
 * session application context (pkg_tenant.set_tenant_context) on the
 * SAME connection that will execute the SQL. This is what makes VPD
 * effective: the context is scoped to the physical connection in use.
 *
 * On connection close, the context is cleared so the pooled connection
 * never leaks tenant context to another request.
 */
public class TenantAwareDataSource implements DataSource {

    private final DataSource delegate;

    public TenantAwareDataSource(DataSource delegate) {
        this.delegate = delegate;
    }

    @Override
    public Connection getConnection() throws SQLException {
        Connection conn = delegate.getConnection();
        applyTenantContext(conn);
        return trackAndClear(conn);
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        Connection conn = delegate.getConnection(username, password);
        applyTenantContext(conn);
        return trackAndClear(conn);
    }

    private void applyTenantContext(Connection conn) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return;
        }
        try (var ps = conn.prepareStatement("BEGIN pkg_tenant.set_tenant_context(?); END;")) {
            ps.setLong(1, tenantId);
            ps.execute();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to set tenant context on connection", e);
        }
    }

    private void clearTenantContext(Connection conn) {
        try {
            if (conn != null && !conn.isClosed()) {
                try (var ps = conn.prepareStatement("BEGIN pkg_tenant.clear_tenant_context; END;")) {
                    ps.execute();
                }
            }
        } catch (SQLException ignored) {
            // Best-effort cleanup; pooled connection will be recycled.
        }
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return delegate.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        delegate.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        delegate.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return delegate.getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return delegate.getParentLogger();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return delegate.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return delegate.isWrapperFor(iface);
    }

    private Connection trackAndClear(Connection conn) {
        return (Connection) java.lang.reflect.Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{ Connection.class },
                (proxy, method, args) -> {
                    if ("close".equals(method.getName())) {
                        clearTenantContext(conn);
                        return null;
                    }
                    return method.invoke(conn, args);
                });
    }
}