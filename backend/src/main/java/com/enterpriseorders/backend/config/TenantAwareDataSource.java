package com.enterpriseorders.backend.config;

import com.enterpriseorders.backend.tenant.port.output.TenantContextProvider;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

/**
 * Tenant-aware DataSource wrapper (SRS §52).
 *
 * Wraps the shared Oracle UCP pool and, per borrow, sets the Oracle
 * session application context (pkg_tenant.set_tenant_context) on the
 * SAME connection that will execute the SQL. This is what makes VPD
 * effective: the context is scoped to the physical connection in use.
 *
 * Design notes:
 * <ul>
 *   <li>M10: schema + tenant context are applied in ONE anonymous PL/SQL
 *       block (1 round-trip per borrow, was 2-3). {@code ALTER SESSION}
 *       runs via {@code EXECUTE IMMEDIATE} since it cannot appear directly
 *       in a PL/SQL block.</li>
 *   <li>H1 (fail-closed, no leak): if session setup fails, the physical
 *       connection is closed (returned to the pool, never leaked) and the
 *       original error is rethrown.</li>
 *   <li>M10/M4: on close the context is cleared only when a tenant was
 *       actually set during THIS borrow — a null-tenant borrow already ran
 *       the clear inside the setup block, so every borrowed session is
 *       normalized at borrow time and returned clean. Clear failures are
 *       retried once, then logged with full context (UCP has no portable
 *       invalidate API; abandoned-connection-timeout is the backstop).</li>
 * </ul>
 */
public class TenantAwareDataSource implements DataSource {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(TenantAwareDataSource.class);

    /** Oracle unquoted-identifier rule; also makes the schema safe to embed in the session block. */
    private static final String SCHEMA_REGEX = "[A-Z_][A-Z0-9_$#]*";

    private static final String CLEAR_TENANT_SQL = "BEGIN pkg_tenant.clear_tenant_context; END;";

    private final DataSource delegate;
    private final TenantContextProvider tenantContext;
    private final String schema;

    /**
     * Schema is mandatory (no default): OracleUcpConfig's {@code oracle.schema}
     * {@code @Value} is the single source of truth. Validated eagerly so a
     * malformed value fails startup instead of failing on first borrow.
     */
    public TenantAwareDataSource(DataSource delegate, TenantContextProvider tenantContext, String schema) {
        if (schema == null || !schema.matches(SCHEMA_REGEX)) {
            throw new IllegalArgumentException(
                    "Invalid oracle.schema '" + schema + "' (must match " + SCHEMA_REGEX + ")");
        }
        this.delegate = delegate;
        this.tenantContext = tenantContext;
        this.schema = schema;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return initAndTrack(delegate.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return initAndTrack(delegate.getConnection(username, password));
    }

    /**
     * H1: any failure while initializing the session returns the physical
     * connection to the pool (close) instead of leaking it, then rethrows.
     */
    private Connection initAndTrack(Connection conn) throws SQLException {
        Long appliedTenant;
        try {
            appliedTenant = applySessionState(conn);
            return proxied(conn, appliedTenant);
        } catch (SQLException | RuntimeException e) {
            try {
                conn.close();
            } catch (SQLException closeFailure) {
                e.addSuppressed(closeFailure);
            }
            throw e;
        }
    }

    /**
     * M10: schema + tenant context in a single round-trip. Ordering is
     * preserved (schema first so name resolution is correct, then context)
     * and the outcome is fail-closed: any error propagates to
     * {@link #initAndTrack}, which closes the connection.
     *
     * @return the tenant id applied to the session, or {@code null} when the
     *         context was cleared (nothing to clear on close)
     */
    private Long applySessionState(Connection conn) throws SQLException {
        Long tenantId = tenantContext.getTenantId();
        try (var ps = conn.prepareStatement(buildSessionBlock(tenantId))) {
            if (tenantId != null) {
                ps.setLong(1, tenantId);
            }
            ps.execute();
        }
        return tenantId;
    }

    /**
     * One anonymous block: schema (validated in the constructor, so safe
     * inside the string literal) + set-or-clear of the tenant context.
     */
    private String buildSessionBlock(Long tenantId) {
        String alterSchema = "EXECUTE IMMEDIATE 'ALTER SESSION SET CURRENT_SCHEMA = " + schema + "'";
        if (tenantId == null) {
            return "BEGIN " + alterSchema + "; pkg_tenant.clear_tenant_context; END;";
        }
        return "BEGIN " + alterSchema + "; pkg_tenant.set_tenant_context(?); END;";
    }

    /**
     * M4: clear on return. Retries once; if both attempts fail the error is
     * logged with tenant + connection identity and the connection is still
     * returned to the pool — UCP exposes no portable invalidate API, so the
     * residual (stale APP_CTX on one pooled session) is bounded by the next
     * borrow-side clear and by abandoned-connection-timeout.
     */
    private static void clearTenantContext(Connection conn, Long tenantId) {
        Exception first = attemptClear(conn);
        if (first == null) {
            return;
        }
        Exception second = attemptClear(conn);
        if (second == null) {
            log.warn("Tenant context clear failed then succeeded on retry (tenant={}, connection={})",
                    tenantId, conn);
            return;
        }
        second.addSuppressed(first);
        log.error("Tenant context clear failed after retry; stale APP_CTX may remain on pooled "
                + "connection (tenant={}, connection={})", tenantId, conn, second);
    }

    private static Exception attemptClear(Connection conn) {
        try {
            if (conn.isClosed()) {
                return null;
            }
            try (var ps = conn.prepareStatement(CLEAR_TENANT_SQL)) {
                ps.execute();
            }
            return null;
        } catch (SQLException | RuntimeException e) {
            return e;
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

    /**
     * L7: one handler per borrow, so its state lives exactly as long as the
     * borrow. unwrap/isWrapperFor resolve {@code Connection} to this guarded
     * proxy; vendor interfaces still unwrap to the raw connection, which
     * bypasses the close-time clear (inherent, documented residual).
     */
    private Connection proxied(Connection conn, Long appliedTenant) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                new GuardedConnection(conn, appliedTenant));
    }

    private static final class GuardedConnection implements InvocationHandler {

        private final Connection target;
        private final Long appliedTenant;

        private GuardedConnection(Connection target, Long appliedTenant) {
            this.target = target;
            this.appliedTenant = appliedTenant;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            switch (method.getName()) {
                case "equals":
                    return proxy == args[0];
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "unwrap":
                    if (args[0] instanceof Class<?> iface && iface.isInstance(proxy)) {
                        return proxy;
                    }
                    break;
                case "isWrapperFor":
                    if (args[0] instanceof Class<?> iface && iface.isInstance(proxy)) {
                        return true;
                    }
                    break;
                case "close":
                    // Skip when no tenant was applied: the borrow-side block
                    // already left the session cleared (M10, saves 1 round-trip).
                    if (appliedTenant != null) {
                        clearTenantContext(target, appliedTenant);
                    }
                    break;
                default:
                    break;
            }
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException e) {
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
        }
    }
}
