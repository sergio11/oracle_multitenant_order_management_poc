package com.enterpriseorders.backend.config;

import com.enterpriseorders.backend.tenant.port.output.TenantContextProvider;
import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.sql.SQLException;

/**
 * Oracle UCP (Universal Connection Pool) configuration with Multitenant Routing.
 *
 * Implements SRS §51: connection pooling using Oracle UCP with timeout
 * and health-check validation on borrow.
 *
 * Supports dynamic routing between:
 *   - Shared Pool (PDB_OLTP) with VPD isolation for STANDARD tenants.
 *   - Dedicated Pool (PDB_CORP) with physical PDB isolation for ENTERPRISE tenants.
 */
@Configuration
public class OracleUcpConfig {

    @Value("${oracle.url}")
    private String url;

    @Value("${oracle.dedicated-url:${oracle.url}}")
    private String dedicatedUrl;

    @Value("${oracle.username}")
    private String username;

    @Value("${oracle.password}")
    private String password;

    @Value("${oracle.ucp.initial-pool-size:3}")
    private int initialPoolSize;

    @Value("${oracle.ucp.min-pool-size:3}")
    private int minPoolSize;

    @Value("${oracle.ucp.max-pool-size:10}")
    private int maxPoolSize;

    // Dedicated (PDB_CORP) pool is lazy by default: no connection is opened
    // until an ENTERPRISE tenant is actually routed there, so the service
    // starts (and stays healthy on the shared pool) when the dedicated PDB
    // is absent or unprovisioned.
    @Value("${oracle.ucp.dedicated.initial-pool-size:0}")
    private int dedicatedInitialPoolSize;

    @Value("${oracle.ucp.dedicated.min-pool-size:0}")
    private int dedicatedMinPoolSize;

    @Value("${oracle.ucp.dedicated.max-pool-size:5}")
    private int dedicatedMaxPoolSize;

    @Value("${oracle.ucp.connection-wait-timeout:30}")
    private int connectionWaitTimeout;

    @Value("${oracle.ucp.inactive-connection-timeout:300}")
    private int inactiveConnectionTimeout;

    @Value("${oracle.ucp.abandoned-connection-timeout:60}")
    private int abandonedConnectionTimeout;

    @Value("${oracle.ucp.validate-connection-on-borrow:true}")
    private boolean validateOnBorrow;

    @Value("${oracle.ucp.connection-validation-timeout:10}")
    private int connectionValidationTimeout;

    @Value("${oracle.schema:APP_OWNER}")
    private String schema;

    @Bean(name = "oraclePoolDataSource", destroyMethod = "")
    public PoolDataSource oraclePoolDataSource() throws SQLException {
        PoolDataSource pds = PoolDataSourceFactory.getPoolDataSource();
        pds.setConnectionFactoryClassName("oracle.jdbc.pool.OracleDataSource");
        pds.setURL(url);
        pds.setUser(username);
        pds.setPassword(password);

        pds.setInitialPoolSize(initialPoolSize);
        pds.setMinPoolSize(minPoolSize);
        pds.setMaxPoolSize(maxPoolSize);

        pds.setConnectionWaitTimeout(connectionWaitTimeout);
        pds.setInactiveConnectionTimeout(inactiveConnectionTimeout);
        pds.setAbandonedConnectionTimeout(abandonedConnectionTimeout);

        pds.setValidateConnectionOnBorrow(validateOnBorrow);
        pds.setConnectionValidationTimeout(connectionValidationTimeout);

        return pds;
    }

    @Bean(name = "oracleDedicatedPoolDataSource", destroyMethod = "")
    public PoolDataSource oracleDedicatedPoolDataSource() throws SQLException {
        PoolDataSource pds = PoolDataSourceFactory.getPoolDataSource();
        pds.setConnectionFactoryClassName("oracle.jdbc.pool.OracleDataSource");
        pds.setURL(dedicatedUrl);
        pds.setUser(username);
        pds.setPassword(password);

        pds.setInitialPoolSize(dedicatedInitialPoolSize);
        pds.setMinPoolSize(dedicatedMinPoolSize);
        pds.setMaxPoolSize(dedicatedMaxPoolSize);

        pds.setConnectionWaitTimeout(connectionWaitTimeout);
        pds.setInactiveConnectionTimeout(inactiveConnectionTimeout);
        pds.setAbandonedConnectionTimeout(abandonedConnectionTimeout);

        pds.setValidateConnectionOnBorrow(validateOnBorrow);
        pds.setConnectionValidationTimeout(connectionValidationTimeout);

        return pds;
    }

    /**
     * Dedicated (PDB_CORP) tenant-aware DataSource, exposed on its own so the
     * tenant-mirroring adapter can talk to the dedicated PDB directly
     * instead of relying on a routing decision it cannot influence.
     */
    @Bean
    public DataSource dedicatedTenantDataSource(
            @Qualifier("oracleDedicatedPoolDataSource") DataSource dedicatedPool,
            TenantContextProvider tenantContext) {
        return new TenantAwareDataSource(dedicatedPool, tenantContext, schema);
    }

    /**
     * Primary DataSource: routes dynamically between shared (VPD) and dedicated (PDB) pools.
     */
    @Bean
    @Primary
    public DataSource dataSource(@Qualifier("oraclePoolDataSource") DataSource sharedPool,
                                 @Qualifier("dedicatedTenantDataSource") DataSource dedicatedAware,
                                 TenantContextProvider tenantContext) {
        DataSource sharedAware = new TenantAwareDataSource(sharedPool, tenantContext, schema);
        return new TenantRoutingDataSource(sharedAware, dedicatedAware, tenantContext);
    }
}
