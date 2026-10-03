package com.enterpriseorders.backend.order.application;

import com.enterpriseorders.backend.exception.BusinessException;
import com.enterpriseorders.backend.web.error.GlobalExceptionHandler;
import com.enterpriseorders.backend.order.model.OrderBO;
import com.enterpriseorders.backend.order.port.output.IdempotencyStore;
import com.enterpriseorders.backend.order.port.output.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotentOperationExecutorTest {

    @Mock
    private IdempotencyStore idempotencyStore;

    @Mock
    private OrderRepository orderRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private IdempotentOperationExecutor executor;

    private static final Long TENANT_ID = 1L;
    private static final Long ORDER_ID = 100L;
    /** Request fingerprint the caller computed for the request under test. */
    private static final String FP = "fp-request-1";
    private OrderBO mockResponse;

    @BeforeEach
    void setUp() {
        executor = new IdempotentOperationExecutor(idempotencyStore, orderRepository, objectMapper);
        mockResponse = new OrderBO(ORDER_ID, "ORD-001", 1L, "CONFIRMED", "USD", null, 0, List.of());
    }

    @Test
    void shouldExecuteActionWhenNoIdempotencyKey() {
        Supplier<OrderBO> action = () -> mockResponse;

        OrderBO result = executor.execute(TENANT_ID, null, "CONFIRM_ORDER", ORDER_ID, null, action);

        assertEquals(ORDER_ID, result.orderId());
        verifyNoInteractions(idempotencyStore);
    }

    @Test
    void shouldRejectBlankKeyWith400() {
        // Closure fix (C5): a present-but-blank key is malformed, not
        // "no idempotency requested" - it used to silently run the action
        // without any idempotency guarantee.
        Supplier<OrderBO> action = mock(Supplier.class);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> executor.execute(TENANT_ID, "  ", "CONFIRM_ORDER", ORDER_ID, FP, action));

        assertEquals("INVALID_IDEMPOTENCY_KEY", ex.getCode());
        assertEquals(400, GlobalExceptionHandler.statusFor(ex.getCode()).value());
        verifyNoInteractions(idempotencyStore);
        verify(action, never()).get();
    }

    @Test
    void shouldRejectKeyLongerThan100Characters() {
        Supplier<OrderBO> action = mock(Supplier.class);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> executor.execute(TENANT_ID, "k".repeat(101), "CONFIRM_ORDER", ORDER_ID, FP, action));

        assertEquals("INVALID_IDEMPOTENCY_KEY", ex.getCode());
        assertEquals(400, GlobalExceptionHandler.statusFor(ex.getCode()).value());
        verifyNoInteractions(idempotencyStore);
        verify(action, never()).get();
    }

    @Test
    void shouldReplayStoredPayloadWhenCompleted() throws Exception {
        String payload = objectMapper.writeValueAsString(mockResponse);
        var record = new IdempotencyStore.IdempotencyRecord(ORDER_ID, "CONFIRM_ORDER", "COMPLETED", payload, FP);
        when(idempotencyStore.findByKey(TENANT_ID, "key-123")).thenReturn(Optional.of(record));

        Supplier<OrderBO> action = mock(Supplier.class);
        OrderBO result = executor.execute(TENANT_ID, "key-123", "CONFIRM_ORDER", ORDER_ID, FP, action);

        assertEquals(ORDER_ID, result.orderId());
        assertEquals("CONFIRMED", result.status());
        verify(action, never()).get();
        verify(orderRepository, never()).findById(anyLong(), anyLong());
        verify(idempotencyStore, never()).insertPending(anyLong(), anyString(), anyString(), anyLong(), anyString());
    }

    @Test
    void shouldReplayLegacyRowWithoutFingerprint() throws Exception {
        // Rows committed before V024 carry a NULL fingerprint: same-key
        // retries keep legacy semantics (fingerprint check skipped).
        String payload = objectMapper.writeValueAsString(mockResponse);
        var record = new IdempotencyStore.IdempotencyRecord(ORDER_ID, "CONFIRM_ORDER", "COMPLETED", payload, null);
        when(idempotencyStore.findByKey(TENANT_ID, "key-pre-v024")).thenReturn(Optional.of(record));

        Supplier<OrderBO> action = mock(Supplier.class);
        OrderBO result = executor.execute(TENANT_ID, "key-pre-v024", "CONFIRM_ORDER", ORDER_ID, FP, action);

        assertEquals(ORDER_ID, result.orderId());
        verify(action, never()).get();
    }

    @Test
    void shouldFallBackToFindByIdWhenPayloadMissing() {
        var record = new IdempotencyStore.IdempotencyRecord(ORDER_ID, "CONFIRM_ORDER", "COMPLETED", null, null);
        when(idempotencyStore.findByKey(TENANT_ID, "key-legacy")).thenReturn(Optional.of(record));
        when(orderRepository.findById(TENANT_ID, ORDER_ID)).thenReturn(mockResponse);

        Supplier<OrderBO> action = mock(Supplier.class);
        OrderBO result = executor.execute(TENANT_ID, "key-legacy", "CONFIRM_ORDER", ORDER_ID, FP, action);

        assertEquals(ORDER_ID, result.orderId());
        verify(orderRepository).findById(TENANT_ID, ORDER_ID);
        verify(action, never()).get();
    }

    @Test
    void shouldRejectReplayWhenPayloadMissingAndAggregateUnknown() {
        var record = new IdempotencyStore.IdempotencyRecord(0L, "CREATE_ORDER", "COMPLETED", null, null);
        when(idempotencyStore.findByKey(TENANT_ID, "key-create-legacy")).thenReturn(Optional.of(record));

        Supplier<OrderBO> action = mock(Supplier.class);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> executor.execute(TENANT_ID, "key-create-legacy", "CREATE_ORDER", 0L, FP, action));

        assertEquals("IDEMPOTENCY_PAYLOAD_MISSING", ex.getCode());
        assertEquals(500, GlobalExceptionHandler.statusFor(ex.getCode()).value());
        verify(action, never()).get();
        verify(orderRepository, never()).findById(anyLong(), anyLong());
    }

    @Test
    void shouldThrowInProgressWhenRecordIsPending() {
        var record = new IdempotencyStore.IdempotencyRecord(ORDER_ID, "CONFIRM_ORDER", "PENDING", null, FP);
        when(idempotencyStore.findByKey(TENANT_ID, "key-pending")).thenReturn(Optional.of(record));

        Supplier<OrderBO> action = mock(Supplier.class);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> executor.execute(TENANT_ID, "key-pending", "CONFIRM_ORDER", ORDER_ID, FP, action));

        assertEquals("IDEMPOTENCY_IN_PROGRESS", ex.getCode());
        assertEquals(409, GlobalExceptionHandler.statusFor(ex.getCode()).value());
        verify(action, never()).get();
        verify(idempotencyStore, never()).insertPending(anyLong(), anyString(), anyString(), anyLong(), anyString());
    }

    @Test
    void shouldRejectWhenFingerprintDiffers() {
        // Closure fix (C4): same key, operation and aggregate, but a
        // DIFFERENT request (e.g. a second payment with another amount).
        // Replaying would falsely report the new request as executed.
        var record = new IdempotencyStore.IdempotencyRecord(ORDER_ID, "PAYMENT", "COMPLETED", null,
                "fp-of-a-different-request");
        when(idempotencyStore.findByKey(TENANT_ID, "key-pay-reuse")).thenReturn(Optional.of(record));

        Supplier<OrderBO> action = mock(Supplier.class);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> executor.execute(TENANT_ID, "key-pay-reuse", "PAYMENT", ORDER_ID, FP, action));

        assertEquals("IDEMPOTENCY_KEY_CONFLICT", ex.getCode());
        assertEquals(409, GlobalExceptionHandler.statusFor(ex.getCode()).value());
        verify(action, never()).get();
        verify(orderRepository, never()).findById(anyLong(), anyLong());
    }

    @Test
    void shouldRejectPendingRecordWhenFingerprintDiffers() {
        // A fingerprint clash wins over the in-progress verdict: the key is
        // already bound to another request, whatever its state.
        var record = new IdempotencyStore.IdempotencyRecord(ORDER_ID, "PAYMENT", "PENDING", null,
                "fp-of-a-different-request");
        when(idempotencyStore.findByKey(TENANT_ID, "key-pay-pending")).thenReturn(Optional.of(record));

        Supplier<OrderBO> action = mock(Supplier.class);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> executor.execute(TENANT_ID, "key-pay-pending", "PAYMENT", ORDER_ID, FP, action));

        assertEquals("IDEMPOTENCY_KEY_CONFLICT", ex.getCode());
        assertEquals(409, GlobalExceptionHandler.statusFor(ex.getCode()).value());
        verify(action, never()).get();
    }

    @Test
    void shouldReexecuteWhenRecordIsFailed() {
        var record = new IdempotencyStore.IdempotencyRecord(ORDER_ID, "CONFIRM_ORDER", "FAILED", null, null);
        when(idempotencyStore.findByKey(TENANT_ID, "key-failed")).thenReturn(Optional.of(record));
        when(idempotencyStore.markCompleted(eq(TENANT_ID), eq("key-failed"), eq(ORDER_ID), anyString())).thenReturn(1);

        OrderBO result = executor.execute(TENANT_ID, "key-failed", "CONFIRM_ORDER", ORDER_ID, FP,
                () -> mockResponse);

        assertEquals(ORDER_ID, result.orderId());
        verify(idempotencyStore, never()).insertPending(anyLong(), anyString(), anyString(), anyLong(), anyString());
        verify(idempotencyStore).markCompleted(eq(TENANT_ID), eq("key-failed"), eq(ORDER_ID), contains("CONFIRMED"));
    }

    @Test
    void shouldExecuteActionAndStoreWhenNewKey() {
        when(idempotencyStore.findByKey(TENANT_ID, "key-456")).thenReturn(Optional.empty());
        when(idempotencyStore.insertPending(TENANT_ID, "key-456", "CONFIRM_ORDER", ORDER_ID, FP)).thenReturn(true);
        when(idempotencyStore.markCompleted(eq(TENANT_ID), eq("key-456"), eq(ORDER_ID), anyString())).thenReturn(1);

        OrderBO result = executor.execute(TENANT_ID, "key-456", "CONFIRM_ORDER", ORDER_ID, FP,
                () -> mockResponse);

        assertEquals(ORDER_ID, result.orderId());
        verify(idempotencyStore).insertPending(TENANT_ID, "key-456", "CONFIRM_ORDER", ORDER_ID, FP);
        verify(idempotencyStore).markCompleted(eq(TENANT_ID), eq("key-456"), eq(ORDER_ID), contains("CONFIRMED"));
    }

    @Test
    void shouldThrowWhenMarkCompletedUpdatesNoRows() {
        when(idempotencyStore.findByKey(TENANT_ID, "key-noupdate")).thenReturn(Optional.empty());
        when(idempotencyStore.insertPending(TENANT_ID, "key-noupdate", "CONFIRM_ORDER", ORDER_ID, FP)).thenReturn(true);
        when(idempotencyStore.markCompleted(eq(TENANT_ID), eq("key-noupdate"), eq(ORDER_ID), anyString())).thenReturn(0);

        assertThrows(IllegalStateException.class,
                () -> executor.execute(TENANT_ID, "key-noupdate", "CONFIRM_ORDER", ORDER_ID, FP,
                        () -> mockResponse));
    }

    @Test
    void shouldThrowRaceConflictWhenInsertFailsAndReReadEmpty() {
        when(idempotencyStore.findByKey(TENANT_ID, "key-race")).thenReturn(Optional.empty());
        when(idempotencyStore.insertPending(TENANT_ID, "key-race", "CONFIRM_ORDER", ORDER_ID, FP)).thenReturn(false);

        Supplier<OrderBO> action = mock(Supplier.class);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> executor.execute(TENANT_ID, "key-race", "CONFIRM_ORDER", ORDER_ID, FP, action));

        assertEquals("IDEMPOTENCY_RACE_CONFLICT", ex.getCode());
        assertEquals(409, GlobalExceptionHandler.statusFor(ex.getCode()).value());
        verify(action, never()).get();
        verify(orderRepository, never()).findById(anyLong(), anyLong());
    }

    @Test
    void shouldReplayWhenRaceLosesToCompletedRecord() throws Exception {
        String payload = objectMapper.writeValueAsString(mockResponse);
        var raceRecord = new IdempotencyStore.IdempotencyRecord(ORDER_ID, "CONFIRM_ORDER", "COMPLETED", payload, FP);
        when(idempotencyStore.findByKey(TENANT_ID, "key-race2"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(raceRecord));
        when(idempotencyStore.insertPending(TENANT_ID, "key-race2", "CONFIRM_ORDER", ORDER_ID, FP)).thenReturn(false);

        Supplier<OrderBO> action = mock(Supplier.class);
        OrderBO result = executor.execute(TENANT_ID, "key-race2", "CONFIRM_ORDER", ORDER_ID, FP, action);

        assertEquals(ORDER_ID, result.orderId());
        verify(action, never()).get();
        verify(orderRepository, never()).findById(anyLong(), anyLong());
    }

    @Test
    void shouldRejectRaceResultWhenFingerprintDiffers() {
        // The loser of a duplicate-key race finds a record bound to a
        // different request: 409, never a replay of someone else's result.
        var raceRecord = new IdempotencyStore.IdempotencyRecord(ORDER_ID, "PAYMENT", "COMPLETED", null,
                "fp-of-a-different-request");
        when(idempotencyStore.findByKey(TENANT_ID, "key-race-fp"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(raceRecord));
        when(idempotencyStore.insertPending(TENANT_ID, "key-race-fp", "PAYMENT", ORDER_ID, FP)).thenReturn(false);

        Supplier<OrderBO> action = mock(Supplier.class);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> executor.execute(TENANT_ID, "key-race-fp", "PAYMENT", ORDER_ID, FP, action));

        assertEquals("IDEMPOTENCY_KEY_CONFLICT", ex.getCode());
        verify(action, never()).get();
    }

    @Test
    void shouldRejectWhenKeyExistsWithDifferentOperation() {
        var record = new IdempotencyStore.IdempotencyRecord(ORDER_ID, "PAYMENT", "COMPLETED", null, null);
        when(idempotencyStore.findByKey(TENANT_ID, "key-op-conflict")).thenReturn(Optional.of(record));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> executor.execute(TENANT_ID, "key-op-conflict", "CONFIRM_ORDER", ORDER_ID, FP,
                        () -> mockResponse));

        assertEquals("IDEMPOTENCY_KEY_CONFLICT", ex.getCode());
        assertEquals(409, GlobalExceptionHandler.statusFor(ex.getCode()).value());
        verify(orderRepository, never()).findById(anyLong(), anyLong());
    }

    @Test
    void shouldTruncateKeyInConflictMessage() {
        String longKey = "0123456789abcdefghijklmnop";
        var record = new IdempotencyStore.IdempotencyRecord(ORDER_ID, "PAYMENT", "COMPLETED", null, null);
        when(idempotencyStore.findByKey(TENANT_ID, longKey)).thenReturn(Optional.of(record));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> executor.execute(TENANT_ID, longKey, "CONFIRM_ORDER", ORDER_ID, FP, () -> mockResponse));

        assertTrue(ex.getMessage().contains("0123456789abcdef"));
        assertFalse(ex.getMessage().contains("0123456789abcdefghijklmn"));
    }

    @Test
    void shouldRejectWhenKeyExistsWithDifferentAggregateId() {
        var record = new IdempotencyStore.IdempotencyRecord(999L, "CONFIRM_ORDER", "COMPLETED", null, null);
        when(idempotencyStore.findByKey(TENANT_ID, "key-agg-conflict")).thenReturn(Optional.of(record));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> executor.execute(TENANT_ID, "key-agg-conflict", "CONFIRM_ORDER", ORDER_ID, FP,
                        () -> mockResponse));

        assertEquals("IDEMPOTENCY_KEY_CONFLICT", ex.getCode());
        assertEquals(409, GlobalExceptionHandler.statusFor(ex.getCode()).value());
        verify(orderRepository, never()).findById(anyLong(), anyLong());
    }

    @Test
    void shouldRejectRaceResultWhenOperationDiffers() {
        var raceRecord = new IdempotencyStore.IdempotencyRecord(ORDER_ID, "PAYMENT", "COMPLETED", null, null);
        when(idempotencyStore.findByKey(TENANT_ID, "key-race-conflict"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(raceRecord));
        when(idempotencyStore.insertPending(TENANT_ID, "key-race-conflict", "CONFIRM_ORDER", ORDER_ID, FP))
                .thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> executor.execute(TENANT_ID, "key-race-conflict", "CONFIRM_ORDER", ORDER_ID, FP,
                        () -> mockResponse));

        assertEquals("IDEMPOTENCY_KEY_CONFLICT", ex.getCode());
        assertEquals(409, GlobalExceptionHandler.statusFor(ex.getCode()).value());
        verify(orderRepository, never()).findById(anyLong(), anyLong());
    }

    @Test
    void shouldMatchCreateSentinelAgainstStoredRealAggregate() throws Exception {
        // CREATE_ORDER binds the key with the 0 sentinel; the COMPLETED row
        // carries the real order id after backfill. A same-key retry with the
        // sentinel must replay, not 409.
        String payload = objectMapper.writeValueAsString(mockResponse);
        var record = new IdempotencyStore.IdempotencyRecord(ORDER_ID, "CREATE_ORDER", "COMPLETED", payload, FP);
        when(idempotencyStore.findByKey(TENANT_ID, "key-create-retry")).thenReturn(Optional.of(record));

        Supplier<OrderBO> action = mock(Supplier.class);
        OrderBO result = executor.execute(TENANT_ID, "key-create-retry", "CREATE_ORDER", 0L, FP, action);

        assertEquals(ORDER_ID, result.orderId());
        verify(action, never()).get();
    }

    @Test
    void shouldBackfillRealAggregateIdOnCompletion() {
        when(idempotencyStore.findByKey(TENANT_ID, "key-create-new")).thenReturn(Optional.empty());
        when(idempotencyStore.insertPending(TENANT_ID, "key-create-new", "CREATE_ORDER", 0L, FP)).thenReturn(true);
        when(idempotencyStore.markCompleted(eq(TENANT_ID), eq("key-create-new"), eq(ORDER_ID), anyString()))
                .thenReturn(1);

        OrderBO result = executor.execute(TENANT_ID, "key-create-new", "CREATE_ORDER", 0L, FP,
                () -> mockResponse);

        assertEquals(ORDER_ID, result.orderId());
        // The created order's id (not the 0 sentinel) is persisted, so later
        // payload-less replays can fall back to findById.
        verify(idempotencyStore).markCompleted(eq(TENANT_ID), eq("key-create-new"), eq(ORDER_ID), anyString());
    }
}
