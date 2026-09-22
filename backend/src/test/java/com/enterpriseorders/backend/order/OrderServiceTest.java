package com.enterpriseorders.backend.order;

import com.enterpriseorders.backend.exception.BusinessException;
import com.enterpriseorders.backend.order.dto.OrderResponse;
import com.enterpriseorders.backend.order.infrastructure.IdempotentOperationExecutor;
import com.enterpriseorders.backend.order.port.output.IdempotencyStore;
import com.enterpriseorders.backend.order.port.output.OrderRepository;
import com.enterpriseorders.backend.tenant.port.output.TenantContextProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private IdempotencyStore idempotencyStore;

    @Mock
    private TenantContextProvider tenantContext;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    private IdempotentOperationExecutor idempotentExecutor;
    private OrderService orderService;

    private static final Long TENANT_ID = 1L;

    @BeforeEach
    void setUp() {
        when(tenantContext.getTenantId()).thenReturn(TENANT_ID);
        idempotentExecutor = new IdempotentOperationExecutor(idempotencyStore, orderRepository);
        orderService = new OrderService(orderRepository, idempotentExecutor, idempotencyStore,
                objectMapper, tenantContext);
    }

    @AfterEach
    void tearDown() {
        reset(tenantContext);
    }

    @Test
    void shouldCreateOrder() {
        when(orderRepository.create(TENANT_ID, 100L, "USD")).thenReturn(1L);
        when(orderRepository.findById(TENANT_ID, 1L)).thenReturn(
                new OrderResponse(1L, "ORD-001", TENANT_ID, 100L, "DRAFT", "USD", null, 0));

        OrderResponse result = orderService.createOrder(100L, "USD");

        assertEquals(1L, result.orderId());
        assertEquals("DRAFT", result.status());
        verify(orderRepository).create(TENANT_ID, 100L, "USD");
        verify(orderRepository).findById(TENANT_ID, 1L);
    }

    @Test
    void shouldAddLineToOrder() {
        when(orderRepository.findById(TENANT_ID, 1L)).thenReturn(
                new OrderResponse(1L, "ORD-001", TENANT_ID, 100L, "DRAFT", "USD", null, 0));

        OrderResponse result = orderService.addLine(1L, 10L, 5L, 20L);

        assertNotNull(result);
        verify(orderRepository).addLine(TENANT_ID, 1L, 10L, 5L, 20L);
    }

    @Test
    void shouldAddLineToPendingOrder() {
        when(orderRepository.findById(TENANT_ID, 1L)).thenReturn(
                new OrderResponse(1L, "ORD-001", TENANT_ID, 100L, "PENDING", "USD", null, 1));

        OrderResponse result = orderService.addLine(1L, 10L, 5L, 20L);

        assertNotNull(result);
        verify(orderRepository).addLine(TENANT_ID, 1L, 10L, 5L, 20L);
    }

    @Test
    void shouldReturnExistingOrderOnIdempotentRetry() {
        OrderResponse pendingOrder = new OrderResponse(1L, "ORD-001", TENANT_ID, 100L, "PENDING", "USD", null, 0);
        OrderResponse confirmedOrder = new OrderResponse(1L, "ORD-001", TENANT_ID, 100L, "CONFIRMED", "USD", null, 1);
        when(orderRepository.findById(TENANT_ID, 1L)).thenReturn(pendingOrder).thenReturn(confirmedOrder);
        when(idempotencyStore.findByKey(TENANT_ID, "test-key"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new IdempotencyStore.IdempotencyRecord(
                        1L, "CONFIRM_ORDER", "COMPLETED")));
        when(idempotencyStore.insertPending(TENANT_ID, "test-key", "CONFIRM_ORDER", 1L)).thenReturn(true);

        OrderResponse first = orderService.confirmOrder(1L, "test-key");
        assertEquals("CONFIRMED", first.status());

        OrderResponse retry = orderService.confirmOrder(1L, "test-key");
        assertEquals("CONFIRMED", retry.status());

        verify(orderRepository, times(1)).confirm(TENANT_ID, 1L, "APP_JAVA", null);
        verify(idempotencyStore, times(1)).markCompleted(eq(TENANT_ID), eq("test-key"), anyString());
    }

    @Test
    void shouldThrowWhenTenantContextNotSet() {
        when(tenantContext.getTenantId()).thenReturn(null);
        assertThrows(IllegalStateException.class, () -> orderService.getOrder(1L));
    }

    @Test
    void shouldGetOrder() {
        when(orderRepository.findById(TENANT_ID, 1L)).thenReturn(
                new OrderResponse(1L, "ORD-001", TENANT_ID, 100L, "DRAFT", "USD", null, 0));

        OrderResponse result = orderService.getOrder(1L);

        assertEquals(1L, result.orderId());
        verify(orderRepository).findById(TENANT_ID, 1L);
    }

    @Test
    void shouldCancelOrder() {
        OrderResponse pendingOrder = new OrderResponse(1L, "ORD-001", TENANT_ID, 100L, "PENDING", "USD", null, 0);
        OrderResponse cancelledOrder = new OrderResponse(1L, "ORD-001", TENANT_ID, 100L, "CANCELLED", "USD", null, 0);
        when(orderRepository.findById(TENANT_ID, 1L)).thenReturn(pendingOrder).thenReturn(cancelledOrder);

        OrderResponse result = orderService.cancelOrder(1L, "Customer request");

        assertEquals("CANCELLED", result.status());
        verify(orderRepository).cancel(TENANT_ID, 1L, "Customer request", "APP_JAVA", null);
    }

    @Test
    void shouldConfirmOrderAndMarkIdempotency() {
        OrderResponse pendingOrder = new OrderResponse(1L, "ORD-001", TENANT_ID, 100L, "PENDING", "USD", null, 0);
        OrderResponse confirmedOrder = new OrderResponse(1L, "ORD-001", TENANT_ID, 100L, "CONFIRMED", "USD", null, 1);
        when(orderRepository.findById(TENANT_ID, 1L)).thenReturn(pendingOrder).thenReturn(confirmedOrder);
        when(idempotencyStore.findByKey(TENANT_ID, "idem-123")).thenReturn(Optional.empty());
        when(idempotencyStore.insertPending(TENANT_ID, "idem-123", "CONFIRM_ORDER", 1L)).thenReturn(true);

        OrderResponse result = orderService.confirmOrder(1L, "idem-123");

        assertEquals("CONFIRMED", result.status());
        verify(idempotencyStore).markCompleted(eq(TENANT_ID), eq("idem-123"), anyString());
    }

    @Test
    void shouldSkipIdempotencyMarkWhenKeyIsNull() {
        OrderResponse pendingOrder = new OrderResponse(1L, "ORD-001", TENANT_ID, 100L, "PENDING", "USD", null, 0);
        OrderResponse confirmedOrder = new OrderResponse(1L, "ORD-001", TENANT_ID, 100L, "CONFIRMED", "USD", null, 1);
        when(orderRepository.findById(TENANT_ID, 1L)).thenReturn(pendingOrder).thenReturn(confirmedOrder);

        OrderResponse result = orderService.confirmOrder(1L, null);

        assertEquals("CONFIRMED", result.status());
        verify(idempotencyStore, never()).markCompleted(anyLong(), anyString(), anyString());
        verify(idempotencyStore, never()).findByKey(anyLong(), anyString());
    }

    @Test
    void shouldRejectAddLineWhenNotDraftOrPending() {
        OrderResponse confirmedOrder = new OrderResponse(1L, "ORD-001", TENANT_ID, 100L, "CONFIRMED", "USD", null, 1);
        when(orderRepository.findById(TENANT_ID, 1L)).thenReturn(confirmedOrder);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.addLine(1L, 10L, 5L, 20L));
        assertEquals("ORDER_NOT_DRAFT", ex.getCode());
        verify(orderRepository, never()).addLine(anyLong(), anyLong(), anyLong(), anyLong(), anyLong());
    }

    @Test
    void shouldRejectConfirmWhenNotPending() {
        OrderResponse confirmedOrder = new OrderResponse(1L, "ORD-001", TENANT_ID, 100L, "CONFIRMED", "USD", null, 1);
        when(orderRepository.findById(TENANT_ID, 1L)).thenReturn(confirmedOrder);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.confirmOrder(1L, "key-123"));
        assertEquals("ORDER_NOT_CONFIRMABLE", ex.getCode());
        verify(orderRepository, never()).confirm(anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    void shouldRejectCancelWhenCompleted() {
        OrderResponse completedOrder = new OrderResponse(1L, "ORD-001", TENANT_ID, 100L, "COMPLETED", "USD", null, 0);
        when(orderRepository.findById(TENANT_ID, 1L)).thenReturn(completedOrder);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.cancelOrder(1L, "Too late"));
        assertEquals("ORDER_NOT_CANCELLABLE", ex.getCode());
    }

    @Test
    void shouldRejectCancelWhenAlreadyCancelled() {
        OrderResponse cancelledOrder = new OrderResponse(1L, "ORD-001", TENANT_ID, 100L, "CANCELLED", "USD", null, 0);
        when(orderRepository.findById(TENANT_ID, 1L)).thenReturn(cancelledOrder);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.cancelOrder(1L, "Already cancelled"));
        assertEquals("ORDER_NOT_CANCELLABLE", ex.getCode());
    }

    @Test
    void shouldRejectPaymentWhenDraft() {
        OrderResponse draftOrder = new OrderResponse(1L, "ORD-001", TENANT_ID, 100L, "DRAFT", "USD", null, 0);
        when(orderRepository.findById(TENANT_ID, 1L)).thenReturn(draftOrder);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.registerPayment(1L, "PAY-001", new java.math.BigDecimal("50.00"), "USD", "CARD", "key-1"));
        assertEquals("PAYMENT_STATE_INVALID", ex.getCode());
        verify(orderRepository, never()).registerPayment(anyLong(), anyLong(), anyString(), any(), anyString(), anyString(), anyString(), anyString());
    }
}
