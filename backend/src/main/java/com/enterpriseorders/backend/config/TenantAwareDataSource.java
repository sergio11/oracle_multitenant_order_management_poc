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

    private Connection trackAndClear(Connection conn) {
        return new ClearContextConnection(conn, this::clearTenantContext);
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

    /**
     * Connection proxy that clears the Oracle tenant context before the
     * connection is returned to the pool.
     */
    static class ClearContextConnection implements Connection {
        private final Connection delegate;
        private final java.util.function.Consumer<Connection> onClose;

        ClearContextConnection(Connection delegate, java.util.function.Consumer<Connection> onClose) {
            this.delegate = delegate;
            this.onClose = onClose;
        }

        @Override
        public void close() throws SQLException {
            onClose.accept(delegate);
            delegate.close();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException { return delegate.unwrap(iface); }
        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException { return delegate.isWrapperFor(iface); }
        @Override
        public java.sql.Statement createStatement() throws SQLException { return delegate.createStatement(); }
        @Override
        public java.sql.PreparedStatement prepareStatement(String sql) throws SQLException { return delegate.prepareStatement(sql); }
        @Override
        public java.sql.CallableStatement prepareCall(String sql) throws SQLException { return delegate.prepareCall(sql); }
        @Override
        public String nativeSQL(String sql) throws SQLException { return delegate.nativeSQL(sql); }
        @Override
        public void setAutoCommit(boolean autoCommit) throws SQLException { delegate.setAutoCommit(autoCommit); }
        @Override
        public boolean getAutoCommit() throws SQLException { return delegate.getAutoCommit(); }
        @Override
        public void commit() throws SQLException { delegate.commit(); }
        @Override
        public void rollback() throws SQLException { delegate.rollback(); }
        @Override
        public boolean isClosed() throws SQLException { return delegate.isClosed(); }
        @Override
        public java.sql.DatabaseMetaData getMetaData() throws SQLException { return delegate.getMetaData(); }
        @Override
        public void setReadOnly(boolean readOnly) throws SQLException { delegate.setReadOnly(readOnly); }
        @Override
        public boolean isReadOnly() throws SQLException { return delegate.isReadOnly(); }
        @Override
        public void setCatalog(String catalog) throws SQLException { delegate.setCatalog(catalog); }
        @Override
        public String getCatalog() throws SQLException { return delegate.getCatalog(); }
        @Override
        public void setTransactionIsolation(int level) throws SQLException { delegate.setTransactionIsolation(level); }
        @Override
        public int getTransactionIsolation() throws SQLException { return delegate.getTransactionIsolation(); }
        @Override
        public java.sql.SQLWarning getWarnings() throws SQLException { return delegate.getWarnings(); }
        @Override
        public void clearWarnings() throws SQLException { delegate.clearWarnings(); }
        @Override
        public java.sql.Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException { return delegate.createStatement(resultSetType, resultSetConcurrency); }
        @Override
        public java.sql.PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException { return delegate.prepareStatement(sql, resultSetType, resultSetConcurrency); }
        @Override
        public java.sql.CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException { return delegate.prepareCall(sql, resultSetType, resultSetConcurrency); }
        @Override
        public java.util.Map<String, Class<?>> getTypeMap() throws SQLException { return delegate.getTypeMap(); }
        @Override
        public void setTypeMap(java.util.Map<String, Class<?>> map) throws SQLException { delegate.setTypeMap(map); }
        @Override
        public void setHoldability(int holdability) throws SQLException { delegate.setHoldability(holdability); }
        @Override
        public int getHoldability() throws SQLException { return delegate.getHoldability(); }
        @Override
        public java.sql.Savepoint setSavepoint() throws SQLException { return delegate.setSavepoint(); }
        @Override
        public java.sql.Savepoint setSavepoint(String name) throws SQLException { return delegate.setSavepoint(name); }
        @Override
        public void rollback(java.sql.Savepoint savepoint) throws SQLException { delegate.rollback(savepoint); }
        @Override
        public void releaseSavepoint(java.sql.Savepoint savepoint) throws SQLException { delegate.releaseSavepoint(savepoint); }
        @Override
        public java.sql.Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException { return delegate.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability); }
        @Override
        public java.sql.PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException { return delegate.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability); }
        @Override
        public java.sql.CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException { return delegate.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability); }
        @Override
        public java.sql.PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException { return delegate.prepareStatement(sql, autoGeneratedKeys); }
        @Override
        public java.sql.PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException { return delegate.prepareStatement(sql, columnIndexes); }
        @Override
        public java.sql.PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException { return delegate.prepareStatement(sql, columnNames); }
    }
}