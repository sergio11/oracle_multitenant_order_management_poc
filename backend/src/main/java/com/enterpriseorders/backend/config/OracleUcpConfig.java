package com.enterpriseorders.backend.config;

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
 * Oracle UCP (Universal Connection Pool) configuration.
 *
 * Implements SRS §51: connection pooling using Oracle UCP with timeout,
 * health-check and RAC-ready behavior. The pool is created programmatically
 * via PoolDataSourceFactory so we control all Oracle-specific settings.
 */
@Configuration
public class OracleUcpConfig {

    @Value("${oracle.url}")
    private String url;

    @Value("${oracle.username}")
    private String username;

    @Value("${oracle.password}")
    private String password;

    @Value("${oracle.ucp.initial-pool-size:5}")
    private int initialPoolSize;

    @Value("${oracle.ucp.min-pool-size:5}")
    private int minPoolSize;

    @Value("${oracle.ucp.max-pool-size:20}")
    private int maxPoolSize;

    @Value("${oracle.ucp.connection-wait-timeout:30}")
    private int connectionWaitTimeout;

    @Value("${oracle.ucp.inactive-connection-timeout:300}")
    private int inactiveConnectionTimeout;

    @Value("${oracle.ucp.validate-connection-on-borrow:true}")
    private boolean validateOnBorrow;

    @Bean(name = "oraclePoolDataSource", destroyMethod = "close")
    public PoolDataSource oraclePoolDataSource() throws SQLException {
        PoolDataSource pds = PoolDataSourceFactory.getPoolDataSource();

        // JDBC driver (Oracle)
        pds.setConnectionFactoryClassName("oracle.jdbc.pool.OracleDataSource");
        pds.setURL(url);
        pds.setUser(username);
        pds.setPassword(password);

        // Pool sizing (SRS §51, FT-002)
        pds.setInitialPoolSize(initialPoolSize);
        pds.setMinPoolSize(minPoolSize);
        pds.setMaxPoolSize(maxPoolSize);

        // Timeouts: avoid application hang on pool exhaustion (FT-002)
        pds.setConnectionWaitTimeout(connectionWaitTimeout);
        pds.setInactiveConnectionTimeout(inactiveConnectionTimeout);

        // Health checks: validate connections before borrow (FT-001)
        pds.setValidateConnectionOnBorrow(validateOnBorrow);
        pds.setConnectionValidationTimeout(10);

        return pds;
    }

    /**
     * Primary DataSource: the tenant-aware wrapper around the UCP pool.
     * Each obtained connection gets the session tenant context applied,
     * which is what makes VPD effective per request (SRS §52).
     */
    @Bean
    @Primary
    public DataSource dataSource(@Qualifier("oraclePoolDataSource") DataSource oraclePool) {
        return new TenantAwareDataSource(oraclePool);
    }
}
</content>
<task_progress>
- [x] FASE 1-5 completadas (f47859d, dded060, d3bd043, 3728f65, 8af9fcb)
- [x] FASE 6: Oracle UCP config class
- [ ] FASE 6: application-local.yml + application-test.yml
- [ ] FASE 6: Clase principal Spring Boot
- [ ] FASE 6: Global error handler
- [ ] FASE 6: Tenant context interceptor + resolver
- [ ] FASE 6: Módulo Tenant (domain/repository/service/controller)
- [ ] FASE 6: Commit fase 6
- [ ] FASE 7 a 12 (resto del plan)
</task_progress>