package com.enterpriseorders.backend.tenant.adapter.persistence;

import com.enterpriseorders.backend.exception.BusinessException;
import com.enterpriseorders.backend.exception.PlsqlErrorMapper;
import com.enterpriseorders.backend.tenant.model.NewTenantBO;
import com.enterpriseorders.backend.tenant.model.TenantBO;
import com.enterpriseorders.backend.tenant.port.output.TenantRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Optional;

@Repository
public class TenantDao implements TenantRepository {

    private final JdbcTemplate jdbcTemplate;

    public TenantDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<Long> findActiveTenantIdByCode(String tenantCode) {
        return jdbcTemplate.query(
                "SELECT tenant_id FROM tenant WHERE tenant_code = ? AND status = 'ACTIVE'",
                rs -> rs.next() ? Optional.of(rs.getLong("tenant_id")) : Optional.empty(),
                tenantCode
        );
    }

    @Override
    public Long create(NewTenantBO request) {
        return jdbcTemplate.execute((Connection connection) -> {
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
                throw mapPlsqlError(ex);
            }
        });
    }

    @Override
    public TenantBO findById(Long tenantId) {
        return jdbcTemplate.query(
                "SELECT tenant_id, tenant_code, name, tier, status, isolation_mode " +
                "FROM tenant WHERE tenant_id = ?",
                rs -> {
                    if (!rs.next()) {
                        throw new BusinessException("TENANT_NOT_FOUND",
                                "Tenant not found: " + tenantId);
                    }
                    return new TenantBO(
                            rs.getLong("tenant_id"),
                            rs.getString("tenant_code"),
                            rs.getString("name"),
                            rs.getString("tier"),
                            rs.getString("status"),
                            rs.getString("isolation_mode"));
                },
                tenantId);
    }

    private RuntimeException mapPlsqlError(SQLException ex) {
        return PlsqlErrorMapper.map(ex);
    }
}
