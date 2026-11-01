package com.enterpriseorders.backend.outbox.adapter.persistence;

import com.enterpriseorders.backend.exception.PlsqlErrorMapper;
import com.enterpriseorders.backend.outbox.port.output.OutboxRelayPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;

/**
 * JDBC adapter for the outbox relay.
 *
 * <p>All the claiming logic (FOR UPDATE SKIP LOCKED, batch limit, state
 * transitions) lives in pkg_outbox: the database is the concurrency
 * authority, not the JVM. This adapter only invokes it and maps errors.
 */
@Repository
public class OutboxRelayDao implements OutboxRelayPort {

    private final JdbcTemplate jdbcTemplate;

    public OutboxRelayDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public int relayOnce(int limit) {
        return jdbcTemplate.execute((Connection connection) -> {
            try (CallableStatement cs = connection.prepareCall(
                    "{ ? = call pkg_outbox.relay_once(?) }")) {
                cs.registerOutParameter(1, Types.NUMERIC);
                cs.setInt(2, limit);
                cs.execute();
                return cs.getInt(1);
            } catch (SQLException ex) {
                throw PlsqlErrorMapper.map(ex);
            }
        });
    }
}
