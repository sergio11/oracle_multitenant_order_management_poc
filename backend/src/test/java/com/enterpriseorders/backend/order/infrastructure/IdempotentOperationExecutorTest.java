package com.enterpriseorders.backend.order.infrastructure;

import com.enterpriseorders.backend.order.dto.OrderResponse;
import com.enterpriseorders.backend.order.port.output.IdempotencyStore;
import com.enterpriseorders.backend.order.port.output.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @InjectMocks
    private IdempotentOperationExecutor executor;

    private static final Long TENANT_ID = 1L;
    private static final Long ORDER_ID = 100L;
    private final OrderResponse mockResponse = new OrderResponse(ORDER_ID, "ORD-001", TENANT_ID, 1L, "CONFIRMED", "USD", null, 0);

    @Test
    void shouldExecuteActionWhenNoIdempotencyKey() {
        Supplier<OrderResponse> action = () -> mockResponse;

        OrderResponse result = executor.execute(TENANT_ID, null, "CONFIRM_ORDER", ORDER_ID, action);

        assertEquals(ORDER_ID, result.orderId());
        verify(idempotencyStore, never()).findByKey(anyLong(), anyString());
    }

    @Test
    void shouldExecuteActionWhenKeyIsBlank() {
        Supplier<OrderResponse> action = () -> mockResponse;

        OrderResponse result = executor.execute(TENANT_ID, "  ", "CONFIRM_ORDER", ORDER_ID, action);

        assertEquals(ORDER_ID, result.orderId());
        verify(idempotencyStore, never()).findByKey(anyLong(), anyString());
    }

    @Test
    void shouldReturnExistingResultWhenKeyExists() {
        var record = new IdempotencyStore.IdempotencyRecord(ORDER_ID, "CONFIRM_ORDER", "COMPLETED");
        when(idempotencyStore.findByKey(TENANT_ID, "key-123")).thenReturn(Optional.of(record));
        when(orderRepository.findById(TENANT_ID, ORDER_ID)).thenReturn(mockResponse);

        Supplier<OrderResponse> action = () -> mockResponse;
        OrderResponse result = executor.execute(TENANT_ID, "key-123", "CONFIRM_ORDER", ORDER_ID, action);

        assertEquals(ORDER_ID, result.orderId());
        verify(idempotencyStore).findByKey(TENANT_ID, "key-123");
        verify(orderRepository).findById(TENANT_ID, ORDER_ID);
        verify(idempotencyStore, never()).insertPending(anyLong(), anyString(), anyString(), anyLong());
    }

    @Test
    void shouldExecuteActionAndStoreWhenNewKey() {
        when(idempotencyStore.findByKey(TENANT_ID, "key-456")).thenReturn(Optional.empty());
        when(idempotencyStore.insertPending(TENANT_ID, "key-456", "CONFIRM_ORDER", ORDER_ID)).thenReturn(true);

        Supplier<OrderResponse> action = () -> mockResponse;
        OrderResponse result = executor.execute(TENANT_ID, "key-456", "CONFIRM_ORDER", ORDER_ID, action);

        assertEquals(ORDER_ID, result.orderId());
        verify(idempotencyStore).insertPending(TENANT_ID, "key-456", "CONFIRM_ORDER", ORDER_ID);
    }

    @Test
    void shouldHandleRaceConditionOnInsert() {
        var raceRecord = new IdempotencyStore.IdempotencyRecord(ORDER_ID, "CONFIRM_ORDER", "COMPLETED");
        when(idempotencyStore.findByKey(TENANT_ID, "key-race"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(raceRecord));
        when(idempotencyStore.insertPending(TENANT_ID, "key-race", "CONFIRM_ORDER", ORDER_ID)).thenReturn(false);
        when(orderRepository.findById(TENANT_ID, ORDER_ID)).thenReturn(mockResponse);

        Supplier<OrderResponse> action = () -> mockResponse;
        OrderResponse result = executor.execute(TENANT_ID, "key-race", "CONFIRM_ORDER", ORDER_ID, action);

        assertEquals(ORDER_ID, result.orderId());
        verify(orderRepository).findById(TENANT_ID, ORDER_ID);
    }

    @Test
    void shouldNotCallActionWhenReturningExistingResult() {
        Supplier<OrderResponse> action = mock(Supplier.class);
        var record = new IdempotencyStore.IdempotencyRecord(ORDER_ID, "CONFIRM_ORDER", "COMPLETED");
        when(idempotencyStore.findByKey(TENANT_ID, "key-cached")).thenReturn(Optional.of(record));
        when(orderRepository.findById(TENANT_ID, ORDER_ID)).thenReturn(mockResponse);

        OrderResponse result = executor.execute(TENANT_ID, "key-cached", "CONFIRM_ORDER", ORDER_ID, action);

        assertEquals(ORDER_ID, result.orderId());
        verify(action, never()).get();
    }

    @Test
    void shouldNotCallActionOnRaceCondition() {
        Supplier<OrderResponse> action = mock(Supplier.class);
        var raceRecord = new IdempotencyStore.IdempotencyRecord(ORDER_ID, "CONFIRM_ORDER", "COMPLETED");
        when(idempotencyStore.findByKey(TENANT_ID, "key-race2"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(raceRecord));
        when(idempotencyStore.insertPending(TENANT_ID, "key-race2", "CONFIRM_ORDER", ORDER_ID)).thenReturn(false);
        when(orderRepository.findById(TENANT_ID, ORDER_ID)).thenReturn(mockResponse);

        OrderResponse result = executor.execute(TENANT_ID, "key-race2", "CONFIRM_ORDER", ORDER_ID, action);

        assertEquals(ORDER_ID, result.orderId());
        verify(action, never()).get();
    }
}
