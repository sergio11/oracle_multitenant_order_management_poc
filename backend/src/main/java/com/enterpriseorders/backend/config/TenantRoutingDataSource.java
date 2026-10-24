package com.enterpriseorders.backend.config;

import com.enterpriseorders.backend.tenant.port.output.TenantContextProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;

/**
 * Dynamic Multitenant Routing DataSource.
 *
 * Implements physical isolation at the PDB level for ENTERPRISE tenants
 * while maintaining shared-schema VPD isolation for STANDARD tenants.
 * Routes dynamically based on TenantContextProvider.
 */
public class TenantRoutingDataSource implements DataSource {

    private static final Logger log = LoggerFactory.getLogger(TenantRoutingDataSource.class);

    private final DataSource sharedDataSource;
    private final DataSource dedicatedDataSource;
    private final TenantContextProvider tenantContext;

    public TenantRoutingDataSource(DataSource sharedDataSource,
                                   DataSource dedicatedDataSource,
                                   TenantContextProvider tenantContext) {
        this.sharedDataSource = sharedDataSource;
        this.dedicatedDataSource = dedicatedDataSource != null ? dedicatedDataSource : sharedDataSource;
        this.tenantContext = tenantContext;
    }

    private DataSource determineTargetDataSource() {
        if (tenantContext != null && "ENTERPRISE".equalsIgnoreCase(tenantContext.getTenantTier())) {
            log.debug("Routing request to DEDICATED PDB pool (tier={})", tenantContext.getTenantTier());
            return dedicatedDataSource;
        }
        return sharedDataSource;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return determineTargetDataSource().getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return determineTargetDataSource().getConnection(username, password);
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return determineTargetDataSource().getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        determineTargetDataSource().setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        determineTargetDataSource().setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return determineTargetDataSource().getLoginTimeout();
    }

    @Override
    public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return determineTargetDataSource().getParentLogger();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        return determineTargetDataSource().unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isInstance(this) || determineTargetDataSource().isWrapperFor(iface);
    }
}
