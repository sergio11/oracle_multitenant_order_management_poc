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
                    when(rs.getString("request_fingerprint")).thenReturn("fp-abc");
                    return extractor.extractData(rs);
                });

        Optional<IdempotencyRecord> result = idempotencyDao.findByKey(9L, "key-1");

        assertTrue(result.isPresent());
        assertEquals(1L, result.get().aggregateId());
        assertEquals("CONFIRM_ORDER", result.get().operation());
        assertEquals("COMPLETED", result.get().status());
        assertEquals("{\"status\":\"CONFIRMED\"}", result.get().responsePayload());
        assertEquals("fp-abc", result.get().requestFingerprint());
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
    @SuppressWarnings("unchecked")
    void shouldMapCreatedAtForReplayWindow() {
        java.time.Instant created = java.time.Instant.parse("2026-01-02T03:04:05Z");
        when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class), eq(9L), eq("key-2")))
                .thenAnswer(inv -> {
                    ResultSetExtractor<Optional<IdempotencyRecord>> extractor = inv.getArgument(1);
                    var rs = mock(java.sql.ResultSet.class);
                    when(rs.next()).thenReturn(true);
                    when(rs.getLong("aggregate_id")).thenReturn(1L);
                    when(rs.getString("operation")).thenReturn("CONFIRM_ORDER");
                    when(rs.getString("status")).thenReturn("COMPLETED");
                    when(rs.getTimestamp("created_at"))
                            .thenReturn(java.sql.Timestamp.from(created));
                    return extractor.extractData(rs);
                });

        Optional<IdempotencyRecord> result = idempotencyDao.findByKey(9L, "key-2");

        assertTrue(result.isPresent());
        assertEquals(created, result.get().createdAt());
    }

    @Test
    void shouldInsertPendingAndReturnTrue() {
        when(jdbcTemplate.update(anyString(), eq(9L), eq("key-1"), eq("CONFIRM_ORDER"), eq(1L), eq("fp-1")))
                .thenReturn(1);

        assertTrue(idempotencyDao.insertPending(9L, "key-1", "CONFIRM_ORDER", 1L, "fp-1"));

        verify(jdbcTemplate).update(contains("request_fingerprint"), eq(9L), eq("key-1"),
                eq("CONFIRM_ORDER"), eq(1L), eq("fp-1"));
    }

    @Test
    void shouldReturnFalseOnDuplicateKey() {
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any()))
                .thenThrow(new DuplicateKeyException("duplicate"));

        assertFalse(idempotencyDao.insertPending(9L, "key-1", "CONFIRM_ORDER", 1L, "fp-1"));
    }

    @Test
    void shouldMarkCompletedAndReturnUpdateCount() {
        when(jdbcTemplate.update(anyString(), eq(7L), eq("{\"status\":\"CONFIRMED\"}"), eq(9L), eq("key-1")))
                .thenReturn(1);

        int updated = idempotencyDao.markCompleted(9L, "key-1", 7L, "{\"status\":\"CONFIRMED\"}");

        assertEquals(1, updated);
        verify(jdbcTemplate).update(contains("COMPLETED"), eq(7L), eq("{\"status\":\"CONFIRMED\"}"),
                eq(9L), eq("key-1"));
    }

    @Test
    void shouldReturnZeroWhenMarkCompletedMatchesNothing() {
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any())).thenReturn(0);

        assertEquals(0, idempotencyDao.markCompleted(9L, "key-1", 7L, "{}"));
    }
}
