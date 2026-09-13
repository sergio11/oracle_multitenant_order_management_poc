package com.enterpriseorders.backend.tenant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * JDBC repository for TENANT lookups (SRS §16 / FR-001).
 *
 * Used by the tenant resolver to validate an incoming X-Tenant-ID and
 * resolve its numeric tenant_id, which is then set as the Oracle session
 * context for VPD.
 */
@Repository
public class TenantDao {

    private final JdbcTemplate jdbcTemplate;

    public TenantDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Returns the tenant_id for a tenant_code if it exists and is ACTIVE.
     */
    public Optional<Long> findActiveTenantIdByCode(String tenantCode) {
        return jdbcTemplate.query(
                "SELECT tenant_id FROM tenant WHERE tenant_code = ? AND status = 'ACTIVE'",
                rs -> rs.next() ? Optional.of(rs.getLong("tenant_id")) : Optional.empty(),
                tenantCode
        );
    }

    /**
     * Returns the tenant_id for a numeric id if it exists and is ACTIVE.
     */
    public Optional<Long> findActiveTenantIdById(Long tenantId) {
        return jdbcTemplate.query(
                "SELECT tenant_id FROM tenant WHERE tenant_id = ? AND status = 'ACTIVE'",
                rs -> rs.next() ? Optional.of(rs.getLong("tenant_id")) : Optional.empty(),
                tenantId
        );
    }
}
</content>