package com.enterpriseorders.backend.tenant;

import com.enterpriseorders.backend.exception.BusinessException;
import com.enterpriseorders.backend.tenant.dto.TenantRequest;
import com.enterpriseorders.backend.tenant.dto.TenantResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;

/**
 * Tenant provisioning service (SRS FR-001, §53).
 *
 * createTenant invokes the Oracle PL/SQL package pkg_tenant.create_tenant,
 * demonstrating that business logic runs close to the data (SRS §32).
 * The operation is wrapped in an explicit transaction (SRS §61).
 */
@Service
public class TenantService {

    private final JdbcTemplate jdbcTemplate;

    public TenantService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public TenantResponse createTenant(TenantRequest request) {
        Long tenantId = executeCreateTenant(request);
        return getTenant(tenantId);
    }

    @Transactional(readOnly = true)
    public TenantResponse getTenant(Long tenantId) {
        return jdbcTemplate.query(
                "SELECT tenant_id, tenant_code, name, tier, status, isolation_mode " +
                "FROM tenant WHERE tenant_id = ?",
                rs -> {
                    if (!rs.next()) {
                        throw new BusinessException("TENANT_NOT_FOUND",
                                "Tenant not found: " + tenantId, 404);
                    }
                    return new TenantResponse(
                            rs.getLong("tenant_id"),
                            rs.getString("tenant_code"),
                            rs.getString("name"),
                            rs.getString("tier"),
                            rs.getString("status"),
                            rs.getString("isolation_mode"));
                },
                tenantId);
    }

    /**
     * Calls pkg_tenant.create_tenant via JDBC callable statement on the
     * current transaction connection.
     */
    private Long executeCreateTenant(TenantRequest request) {
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

    /**
     * Maps PL/SQL RAISE_APPLICATION_ERROR codes from the packages (SRS §54)
     * to functional BusinessExceptions without exposing ORA-* to clients.
     */
    private BusinessException mapPlsqlError(SQLException ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage();
        // pkg_tenant.create_tenant raises -20001 for duplicate code
        if (message.contains("ORA-20001")) {
            return new BusinessException("TENANT_ALREADY_EXISTS", "Tenant code already exists", 409);
        }
        if (message.contains("ORA-20002")) {
            return new BusinessException("INVALID_TIER", "Invalid tier value", 400);
        }
        if (message.contains("ORA-20003")) {
            return new BusinessException("INVALID_ISOLATION_MODE", "Invalid isolation mode", 400);
        }
        return new BusinessException("DATABASE_ERROR", "Tenant creation failed");
    }
}
</content>