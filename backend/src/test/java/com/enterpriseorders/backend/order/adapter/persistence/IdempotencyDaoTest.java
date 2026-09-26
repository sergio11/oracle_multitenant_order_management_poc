package com.enterpriseorders.backend.order.adapter.persistence;

import com.enterpriseorders.backend.order.port.output.IdempotencyStore.IdempotencyRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyDaoTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @InjectMocks
    private IdempotencyDao idempotencyDao;

    @Test
    @SuppressWarnings("unchecked")
    void shouldFindByKeyAndMapAllColumns() {
        when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class), eq(9L), eq("key-1")))
                .thenAnswer(inv -> {
                    ResultSetExtractor<Optional<IdempotencyRecord>> extractor = inv.getArgument(1);
                    var rs = mock(java.sql.ResultSet.class);
                    when(rs.next()).thenReturn(true);
                    when(rs.getLong("aggregate_id")).thenReturn(1L);
                    when(rs.getString("operation")).thenReturn("CONFIRM_ORDER");
                    when(rs.getString("status")).thenReturn("COMPLETED");
                    when(rs.getString("response_payload")).thenReturn("{\"status\":\"CONFIRMED\"}");
                    return extractor.extractData(rs);
                });

        Optional<IdempotencyRecord> result = idempotencyDao.findByKey(9L, "key-1");

        assertTrue(result.isPresent());
        assertEquals(1L, result.get().aggregateId());
        assertEquals("CONFIRM_ORDER", result.get().operation());
        assertEquals("COMPLETED", result.get().status());
        assertEquals("{\"status\":\"CONFIRMED\"}", result.get().responsePayload());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnEmptyWhenKeyNotFound() {
        when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class), eq(9L), eq("missing")))
                .thenAnswer(inv -> {
                    ResultSetExtractor<Optional<IdempotencyRecord>> extractor = inv.getArgument(1);
                    var rs = mock(java.sql.ResultSet.class);
                    when(rs.next()).thenReturn(false);
                    return extractor.extractData(rs);
                });

        assertTrue(idempotencyDao.findByKey(9L, "missing").isEmpty());
    }

    @Test
    void shouldInsertPendingAndReturnTrue() {
        when(jdbcTemplate.update(anyString(), eq(9L), eq("key-1"), eq("CONFIRM_ORDER"), eq(1L)))
                .thenReturn(1);

        assertTrue(idempotencyDao.insertPending(9L, "key-1", "CONFIRM_ORDER", 1L));

        verify(jdbcTemplate).update(contains("idempotency_key"), eq(9L), eq("key-1"),
                eq("CONFIRM_ORDER"), eq(1L));
    }

    @Test
    void shouldReturnFalseOnDuplicateKey() {
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any()))
                .thenThrow(new DuplicateKeyException("duplicate"));

        assertFalse(idempotencyDao.insertPending(9L, "key-1", "CONFIRM_ORDER", 1L));
    }

    @Test
    void shouldMarkCompletedAndReturnUpdateCount() {
        when(jdbcTemplate.update(anyString(), eq("{\"status\":\"CONFIRMED\"}"), eq(9L), eq("key-1")))
                .thenReturn(1);

        int updated = idempotencyDao.markCompleted(9L, "key-1", "{\"status\":\"CONFIRMED\"}");

        assertEquals(1, updated);
        verify(jdbcTemplate).update(contains("COMPLETED"), eq("{\"status\":\"CONFIRMED\"}"),
                eq(9L), eq("key-1"));
    }

    @Test
    void shouldReturnZeroWhenMarkCompletedMatchesNothing() {
        when(jdbcTemplate.update(anyString(), any(), any(), any())).thenReturn(0);

        assertEquals(0, idempotencyDao.markCompleted(9L, "key-1", "{}"));
    }
}
