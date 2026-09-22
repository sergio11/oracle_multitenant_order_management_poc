package com.enterpriseorders.backend.config;

import com.enterpriseorders.backend.tenant.port.output.TenantContextProvider;

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
    private final TenantContextProvider tenantContext;
    private final String schema;

    public TenantAwareDataSource(DataSource delegate, TenantContextProvider tenantContext) {
        this(delegate, tenantContext, "APP_OWNER");
    }

    public TenantAwareDataSource(DataSource delegate, TenantContextProvider tenantContext, String schema) {
        this.delegate = delegate;
        this.tenantContext = tenantContext;
        this.schema = schema;
    }

    @Override
    public Connection getConnection() throws SQLException {
        Connection conn = delegate.getConnection();
        applySchema(conn);
        applyTenantContext(conn);
        return trackAndClear(conn);
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        Connection conn = delegate.getConnection(username, password);
        applySchema(conn);
        applyTenantContext(conn);
        return trackAndClear(conn);
    }

    /**
     * DAOs use unqualified object names (tenant, sales_order, pkg_*); the
     * runtime user (APP_RUNTIME) owns nothing, so resolve everything against
     * the owner schema. Privileges are unchanged - only name resolution.
     */
    private void applySchema(Connection conn) throws SQLException {
        try (var ps = conn.prepareStatement("ALTER SESSION SET CURRENT_SCHEMA = " + schema)) {
            ps.execute();
        }
    }

    private void applyTenantContext(Connection conn) {
        Long tenantId = tenantContext.getTenantId();
        if (tenantId == null) {
            clearTenantContext(conn);
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
                    try {
                        if ("close".equals(method.getName())) {
                            clearTenantContext(conn);
                        }
                        return method.invoke(conn, args);
                    } catch (java.lang.reflect.InvocationTargetException e) {
                        Throwable cause = e.getCause() != null ? e.getCause() : e;
                        if (cause instanceof RuntimeException runtimeException) {
                            throw runtimeException;
                        }
                        if (cause instanceof Error error) {
                            throw error;
                        }
                        if (cause instanceof SQLException sqlException) {
                            throw sqlException;
                        }
                        throw new SQLException("Error invoking connection method: " + method.getName(), cause);
                    }
                });
    }
}