package com.enterpriseorders.backend.tenant.adapter.persistence;

import com.enterpriseorders.backend.exception.BusinessException;
import com.enterpriseorders.backend.exception.PlsqlErrorMapper;
import com.enterpriseorders.backend.tenant.model.NewTenantBO;
import com.enterpriseorders.backend.tenant.port.output.DedicatedTenantProvisioner;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Optional;

/**
 * {@link DedicatedTenantProvisioner} backed by the dedicated (PDB_CORP)
 * DataSource.
 *
 * <p>It deliberately does NOT go through {@code TenantDao}: the routing
 * DataSource decides from the request's tenant context, which at creation
 * time belongs to the *caller*, not to the tenant being created. This
 * adapter talks to the dedicated pool directly, so the mirror happens on a
 * connection that cannot be routed elsewhere.
 *
 * <p>When both configured URLs point at the same database (the {@code prod}
 * default), the "mirror" degenerates to a read of the row the shared write
 * just created: {@code ensureProvisioned} finds it and returns its id.
 */
@Repository
public class DedicatedTenantProvisionerDao implements DedicatedTenantProvisioner {

    private static final String SELECT_ID =
            "SELECT tenant_id FROM tenant WHERE tenant_code = ? AND status = 'ACTIVE'";

    /** Own JdbcTemplate instance (not a bean) to avoid a second JdbcTemplate
     *  bean competing with the auto-configured primary one. */
    private final JdbcTemplate dedicated;

    public DedicatedTenantProvisionerDao(
            @Qualifier("dedicatedTenantDataSource") DataSource dedicatedDataSource) {
        this.dedicated = new JdbcTemplate(dedicatedDataSource);
    }

    @Override
    public long ensureProvisioned(NewTenantBO request) {
        String code = request.tenantCode().trim();
        Optional<Long> existing = findId(code);
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            return create(request);
        } catch (BusinessException ex) {
            if (!"TENANT_ALREADY_EXISTS".equals(ex.getCode())) {
                throw ex;
            }
            // Lost a race: the row landed between read and create.
            return findId(code).orElseThrow(() -> ex);
        }
    }

    private Optional<Long> findId(String tenantCode) {
        return dedicated.query(
                SELECT_ID,
                rs -> rs.next() ? Optional.of(rs.getLong("tenant_id")) : Optional.empty(),
                tenantCode);
    }

    private long create(NewTenantBO request) {
        return dedicated.execute((Connection connection) -> {
            try (CallableStatement cs = connection.prepareCall(
                    "{ ? = call pkg_tenant.create_tenant(?, ?, ?, ?) }")) {
                cs.registerOutParameter(1, Types.NUMERIC);
                cs.setString(2, request.tenantCode().trim());
                cs.setString(3, request.name().trim());
                cs.setString(4, request.tier());
                cs.setString(5, request.isolationMode());
                cs.execute();
                return cs.getBigDecimal(1).longValue();
            } catch (SQLException ex) {
                throw PlsqlErrorMapper.map(ex);
            }
        });
    }
}
