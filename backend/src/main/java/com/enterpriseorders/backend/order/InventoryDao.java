package com.enterpriseorders.backend.order;

import com.enterpriseorders.backend.exception.PlsqlErrorMapper;
import com.enterpriseorders.backend.order.port.output.InventoryRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;

@Repository
public class InventoryDao implements InventoryRepository {

    private final JdbcTemplate jdbcTemplate;

    public InventoryDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public long getAvailableStock(Long tenantId, Long warehouseId, Long productId) {
        return jdbcTemplate.execute((Connection connection) -> {
            try (CallableStatement cs = connection.prepareCall(
                    "{ ? = call pkg_inventory.get_available_stock(?, ?, ?) }")) {
                cs.registerOutParameter(1, Types.NUMERIC);
                cs.setLong(2, tenantId);
                cs.setLong(3, warehouseId);
                cs.setLong(4, productId);
                cs.execute();
                return cs.getBigDecimal(1).longValue();
            } catch (SQLException ex) {
                throw PlsqlErrorMapper.map(ex);
            }
        });
    }
}