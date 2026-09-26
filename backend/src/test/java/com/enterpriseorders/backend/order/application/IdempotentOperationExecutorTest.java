package com.enterpriseorders.backend.order.application;

import com.enterpriseorders.backend.exception.BusinessException;
import com.enterpriseorders.backend.order.model.OrderView;
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
    private OrderView mockResponse;

    @BeforeEach
    void setUp() {
        executor = new IdempotentOperationExecutor(idempotencyStore, orderRepository, objectMapper);
        mockResponse = new OrderView(ORDER_ID, "ORD-001", 1L, "CONFIRMED", "USD", null, 0, List.of());
    }

    @Test
    void shouldExecuteActionWhenNoIdempotencyKey() {
        Supplier<OrderView> action = () -> mockResponse;

        OrderView result = executor.execute(TENANT_ID, null, "CONFIRM_ORDER", ORDER_ID, action);

        assertEquals(ORDER_ID, result.orderId());
        verifyNoInteractions(idempotencyStore);
    }

    @Test
    void shouldExecuteActionWhenKeyIsBlank() {
        Supplier<OrderView> action = () -> mockResponse;

        OrderView result = executor.execute(TENANT_ID, "  ", "CONFIRM_ORDER", ORDER_ID, action);

        assertEquals(ORDER_ID, result.orderId());
        verifyNoInteractions(idempotencyStore);
    }

    @Test
    void shouldRejectKeyLongerThan100Characters() {
        Supplier<OrderView> action = mock(Supplier.class);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> executor.execute(TENANT_ID, "k".repeat(101), "CONFIRM_ORDER", ORDER_ID, action));

        assertEquals("INVALID_IDEMPOTENCY_KEY", ex.getCode());
        assertEquals(400, ex.getHttpStatus());
        verifyNoInteractions(idempotencyStore);
        verify(action, never()).get();
    }

    @Test
    void shouldReplayStoredPayloadWhenCompleted() throws Exception {
        String payload = objectMapper.writeValueAsString(mockResponse);
        var record = new IdempotencyStore.IdempotencyRecord(ORDER_ID, "CONFIRM_ORDER", "COMPLETED", payload);
        when(idempotencyStore.findByKey(TENANT_ID, "key-123")).thenReturn(Optional.of(record));

        Supplier<OrderView> action = mock(Supplier.class);
        OrderView result = executor.execute(TENANT_ID, "key-123", "CONFIRM_ORDER", ORDER_ID, action);

        assertEquals(ORDER_ID, result.orderId());
        assertEquals("CONFIRMED", result.status());
        verify(action, never()).get();
        verify(orderRepository, never()).findById(anyLong(), anyLong());
        verify(idempotencyStore, never()).insertPending(anyLong(), anyString(), anyString(), anyLong());
    }

    @Test
    void shouldFallBackToFindByIdWhenPayloadMissing() {
        var record = new IdempotencyStore.IdempotencyRecord(ORDER_ID, "CONFIRM_ORDER", "COMPLETED", null);
        when(idempotencyStore.findByKey(TENANT_ID, "key-legacy")).thenReturn(Optional.of(record));
        when(orderRepository.findById(TENANT_ID, ORDER_ID)).thenReturn(mockResponse);

        Supplier<OrderView> action = mock(Supplier.class);
        OrderView result = executor.execute(TENANT_ID, "key-legacy", "CONFIRM_ORDER", ORDER_ID, action);

        assertEquals(ORDER_ID, result.orderId());
        verify(orderRepository).findById(TENANT_ID, ORDER_ID);
        verify(action, never()).get();
    }

    @Test
    void shouldRejectReplayWhenPayloadMissingAndAggregateUnknown() {
        var record = new IdempotencyStore.IdempotencyRecord(0L, "CREATE_ORDER", "COMPLETED", null);
        when(idempotencyStore.findByKey(TENANT_ID, "key-create-legacy")).thenReturn(Optional.of(record));

        Supplier<OrderView> action = mock(Supplier.class);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> executor.execute(TENANT_ID, "key-create-legacy", "CREATE_ORDER", 0L, action));

        assertEquals("IDEMPOTENCY_PAYLOAD_MISSING", ex.getCode());
        assertEquals(500, ex.getHttpStatus());
        verify(action, never()).get();
        verify(orderRepository, never()).findById(anyLong(), anyLong());
    }

    @Test
    void shouldThrowInProgressWhenRecordIsPending() {
        var record = new IdempotencyStore.IdempotencyRecord(ORDER_ID, "CONFIRM_ORDER", "PENDING", null);
        when(idempotencyStore.findByKey(TENANT_ID, "key-pending")).thenReturn(Optional.of(record));

        Supplier<OrderView> action = mock(Supplier.class);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> executor.execute(TENANT_ID, "key-pending", "CONFIRM_ORDER", ORDER_ID, action));

        assertEquals("IDEMPOTENCY_IN_PROGRESS", ex.getCode());
        assertEquals(409, ex.getHttpStatus());
        verify(action, never()).get();
        verify(idempotencyStore, never()).insertPending(anyLong(), anyString(), anyString(), anyLong());
    }

    @Test
    void shouldReexecuteWhenRecordIsFailed() {
        var record = new IdempotencyStore.IdempotencyRecord(ORDER_ID, "CONFIRM_ORDER", "FAILED", null);
        when(idempotencyStore.findByKey(TENANT_ID, "key-failed")).thenReturn(Optional.of(record));
        when(idempotencyStore.markCompleted(eq(TENANT_ID), eq("key-failed"), anyString())).thenReturn(1);

        OrderView result = executor.execute(TENANT_ID, "key-failed", "CONFIRM_ORDER", ORDER_ID,
                () -> mockResponse);

        assertEquals(ORDER_ID, result.orderId());
        verify(idempotencyStore, never()).insertPending(anyLong(), anyString(), anyString(), anyLong());
        verify(idempotencyStore).markCompleted(eq(TENANT_ID), eq("key-failed"), contains("CONFIRMED"));
    }

    @Test
    void shouldExecuteActionAndStoreWhenNewKey() {
        when(idempotencyStore.findByKey(TENANT_ID, "key-456")).thenReturn(Optional.empty());
        when(idempotencyStore.insertPending(TENANT_ID, "key-456", "CONFIRM_ORDER", ORDER_ID)).thenReturn(true);
        when(idempotencyStore.markCompleted(eq(TENANT_ID), eq("key-456"), anyString())).thenReturn(1);

        OrderView result = executor.execute(TENANT_ID, "key-456", "CONFIRM_ORDER", ORDER_ID,
                () -> mockResponse);

        assertEquals(ORDER_ID, result.orderId());
        verify(idempotencyStore).insertPending(TENANT_ID, "key-456", "CONFIRM_ORDER", ORDER_ID);
        verify(idempotencyStore).markCompleted(eq(TENANT_ID), eq("key-456"), contains("CONFIRMED"));
    }

    @Test
    void shouldThrowWhenMarkCompletedUpdatesNoRows() {
        when(idempotencyStore.findByKey(TENANT_ID, "key-noupdate")).thenReturn(Optional.empty());
        when(idempotencyStore.insertPending(TENANT_ID, "key-noupdate", "CONFIRM_ORDER", ORDER_ID)).thenReturn(true);
        when(idempotencyStore.markCompleted(eq(TENANT_ID), eq("key-noupdate"), anyString())).thenReturn(0);

        assertThrows(IllegalStateException.class,
                () -> executor.execute(TENANT_ID, "key-noupdate", "CONFIRM_ORDER", ORDER_ID,
                        () -> mockResponse));
    }

    @Test
    void shouldThrowRaceConflictWhenInsertFailsAndReReadEmpty() {
        when(idempotencyStore.findByKey(TENANT_ID, "key-race")).thenReturn(Optional.empty());
        when(idempotencyStore.insertPending(TENANT_ID, "key-race", "CONFIRM_ORDER", ORDER_ID)).thenReturn(false);

        Supplier<OrderView> action = mock(Supplier.class);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> executor.execute(TENANT_ID, "key-race", "CONFIRM_ORDER", ORDER_ID, action));

        assertEquals("IDEMPOTENCY_RACE_CONFLICT", ex.getCode());
        assertEquals(409, ex.getHttpStatus());
        verify(action, never()).get();
        verify(orderRepository, never()).findById(anyLong(), anyLong());
    }

    @Test
    void shouldReplayWhenRaceLosesToCompletedRecord() throws Exception {
        String payload = objectMapper.writeValueAsString(mockResponse);
        var raceRecord = new IdempotencyStore.IdempotencyRecord(ORDER_ID, "CONFIRM_ORDER", "COMPLETED", payload);
        when(idempotencyStore.findByKey(TENANT_ID, "key-race2"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(raceRecord));
        when(idempotencyStore.insertPending(TENANT_ID, "key-race2", "CONFIRM_ORDER", ORDER_ID)).thenReturn(false);

        Supplier<OrderView> action = mock(Supplier.class);
        OrderView result = executor.execute(TENANT_ID, "key-race2", "CONFIRM_ORDER", ORDER_ID, action);

        assertEquals(ORDER_ID, result.orderId());
        verify(action, never()).get();
        verify(orderRepository, never()).findById(anyLong(), anyLong());
    }

    @Test
    void shouldRejectWhenKeyExistsWithDifferentOperation() {
        var record = new IdempotencyStore.IdempotencyRecord(ORDER_ID, "PAYMENT", "COMPLETED", null);
        when(idempotencyStore.findByKey(TENANT_ID, "key-op-conflict")).thenReturn(Optional.of(record));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> executor.execute(TENANT_ID, "key-op-conflict", "CONFIRM_ORDER", ORDER_ID, () -> mockResponse));

        assertEquals("IDEMPOTENCY_KEY_CONFLICT", ex.getCode());
        assertEquals(409, ex.getHttpStatus());
        verify(orderRepository, never()).findById(anyLong(), anyLong());
    }

    @Test
    void shouldTruncateKeyInConflictMessage() {
        String longKey = "0123456789abcdefghijklmnop";
        var record = new IdempotencyStore.IdempotencyRecord(ORDER_ID, "PAYMENT", "COMPLETED", null);
        when(idempotencyStore.findByKey(TENANT_ID, longKey)).thenReturn(Optional.of(record));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> executor.execute(TENANT_ID, longKey, "CONFIRM_ORDER", ORDER_ID, () -> mockResponse));

        assertTrue(ex.getMessage().contains("0123456789abcdef"));
        assertFalse(ex.getMessage().contains("0123456789abcdefghijklmn"));
    }

    @Test
    void shouldRejectWhenKeyExistsWithDifferentAggregateId() {
        var record = new IdempotencyStore.IdempotencyRecord(999L, "CONFIRM_ORDER", "COMPLETED", null);
        when(idempotencyStore.findByKey(TENANT_ID, "key-agg-conflict")).thenReturn(Optional.of(record));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> executor.execute(TENANT_ID, "key-agg-conflict", "CONFIRM_ORDER", ORDER_ID, () -> mockResponse));

        assertEquals("IDEMPOTENCY_KEY_CONFLICT", ex.getCode());
        assertEquals(409, ex.getHttpStatus());
        verify(orderRepository, never()).findById(anyLong(), anyLong());
    }

    @Test
    void shouldRejectRaceResultWhenOperationDiffers() {
        var raceRecord = new IdempotencyStore.IdempotencyRecord(ORDER_ID, "PAYMENT", "COMPLETED", null);
        when(idempotencyStore.findByKey(TENANT_ID, "key-race-conflict"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(raceRecord));
        when(idempotencyStore.insertPending(TENANT_ID, "key-race-conflict", "CONFIRM_ORDER", ORDER_ID)).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> executor.execute(TENANT_ID, "key-race-conflict", "CONFIRM_ORDER", ORDER_ID, () -> mockResponse));

        assertEquals("IDEMPOTENCY_KEY_CONFLICT", ex.getCode());
        assertEquals(409, ex.getHttpStatus());
        verify(orderRepository, never()).findById(anyLong(), anyLong());
    }
}
