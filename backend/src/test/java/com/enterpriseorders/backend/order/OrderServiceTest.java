package com.enterpriseorders.backend.order;

import com.enterpriseorders.backend.exception.BusinessException;
import com.enterpriseorders.backend.execution.ExecutionContext;
import com.enterpriseorders.backend.web.error.GlobalExceptionHandler;
import com.enterpriseorders.backend.order.application.IdempotentOperationExecutor;
import com.enterpriseorders.backend.order.application.OrderService;
import com.enterpriseorders.backend.order.model.OrderView;
import com.enterpriseorders.backend.order.port.output.IdempotencyStore;
import com.enterpriseorders.backend.order.port.output.OrderRepository;
import com.enterpriseorders.backend.tenant.port.output.TenantContextProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
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

    @Mock
    private ExecutionContext executionContext;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private IdempotentOperationExecutor idempotentExecutor;
    private OrderService orderService;

    private static final Long TENANT_ID = 1L;

    @BeforeEach
    void setUp() {
        lenient().when(tenantContext.getTenantId()).thenReturn(TENANT_ID);
        lenient().when(executionContext.actor()).thenReturn("APP_JAVA");
        lenient().when(executionContext.correlationId()).thenReturn(null);
        idempotentExecutor = new IdempotentOperationExecutor(idempotencyStore, orderRepository, objectMapper);
        orderService = new OrderService(orderRepository, idempotentExecutor, tenantContext, executionContext);
    }

    @Test
    void shouldCreateOrder() {
        when(orderRepository.create(TENANT_ID, 100L, "USD")).thenReturn(1L);
        when(orderRepository.findById(TENANT_ID, 1L)).thenReturn(
                new OrderView(1L, "ORD-001", 100L, "DRAFT", "USD", null, 0, List.of()));

        OrderView result = orderService.createOrder(100L, "USD", null);

        assertEquals(1L, result.orderId());
        assertEquals("DRAFT", result.status());
        verify(orderRepository).create(TENANT_ID, 100L, "USD");
        verify(orderRepository).findById(TENANT_ID, 1L);
        verifyNoInteractions(idempotencyStore);
    }

    @Test
    void shouldCreateOrderIdempotently() throws Exception {
        OrderView draft = new OrderView(1L, "ORD-001", 100L, "DRAFT", "USD", null, 0, List.of());
        String payload = objectMapper.writeValueAsString(draft);
        when(orderRepository.create(TENANT_ID, 100L, "USD")).thenReturn(1L);
        when(orderRepository.findById(TENANT_ID, 1L)).thenReturn(draft);
        when(idempotencyStore.findByKey(TENANT_ID, "create-key"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new IdempotencyStore.IdempotencyRecord(
                        0L, "CREATE_ORDER", "COMPLETED", payload, null)));
        when(idempotencyStore.insertPending(eq(TENANT_ID), eq("create-key"), eq("CREATE_ORDER"), eq(0L), anyString())).thenReturn(true);
        when(idempotencyStore.markCompleted(eq(TENANT_ID), eq("create-key"), eq(1L), anyString())).thenReturn(1);

        OrderView first = orderService.createOrder(100L, "USD", "create-key");
        OrderView retry = orderService.createOrder(100L, "USD", "create-key");

        assertEquals(1L, first.orderId());
        assertEquals(1L, retry.orderId());
        assertEquals("DRAFT", retry.status());
        verify(orderRepository, times(1)).create(TENANT_ID, 100L, "USD");
        verify(idempotencyStore, times(1)).markCompleted(eq(TENANT_ID), eq("create-key"), eq(1L), anyString());
    }

    @Test
    void shouldRejectCreateWhenKeyReusedWithDifferentParams() throws Exception {
        // Same key, different customer/currency: the stored COMPLETED record
        // belongs to another order. Must 409, never silently return it.
        OrderView firstOrder = new OrderView(1L, "ORD-001", 100L, "DRAFT", "USD", null, 0, List.of());
        String payload = objectMapper.writeValueAsString(firstOrder);
        when(idempotencyStore.findByKey(TENANT_ID, "shared-key"))
                .thenReturn(Optional.of(new IdempotencyStore.IdempotencyRecord(
                        1L, "CREATE_ORDER", "COMPLETED", payload, null)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.createOrder(200L, "EUR", "shared-key"));

        assertEquals("IDEMPOTENCY_KEY_CONFLICT", ex.getCode());
        assertEquals(409, GlobalExceptionHandler.statusFor(ex.getCode()).value());
        verify(orderRepository, never()).create(anyLong(), anyLong(), anyString());
    }

    @Test
    void shouldAddLineToOrder() {
        when(orderRepository.findById(TENANT_ID, 1L)).thenReturn(
                new OrderView(1L, "ORD-001", 100L, "DRAFT", "USD", null, 0, List.of()));

        OrderView result = orderService.addLine(1L, 10L, 5L);

        assertNotNull(result);
        verify(orderRepository).addLine(TENANT_ID, 1L, 10L, 5L);
    }

    @Test
    void shouldAddLineToPendingOrder() {
        when(orderRepository.findById(TENANT_ID, 1L)).thenReturn(
                new OrderView(1L, "ORD-001", 100L, "PENDING", "USD", null, 1, List.of()));

        OrderView result = orderService.addLine(1L, 10L, 5L);

        assertNotNull(result);
        verify(orderRepository).addLine(TENANT_ID, 1L, 10L, 5L);
    }

    @Test
    void shouldReturnExistingOrderOnIdempotentRetry() throws Exception {
        OrderView pendingOrder = new OrderView(1L, "ORD-001", 100L, "PENDING", "USD", null, 0, List.of());
        OrderView confirmedOrder = new OrderView(1L, "ORD-001", 100L, "CONFIRMED", "USD", null, 1, List.of());
        String payload = objectMapper.writeValueAsString(confirmedOrder);
        when(orderRepository.findById(TENANT_ID, 1L)).thenReturn(pendingOrder).thenReturn(confirmedOrder);
        when(idempotencyStore.findByKey(TENANT_ID, "test-key"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new IdempotencyStore.IdempotencyRecord(
                        1L, "CONFIRM_ORDER", "COMPLETED", payload, null)));
        when(idempotencyStore.insertPending(eq(TENANT_ID), eq("test-key"), eq("CONFIRM_ORDER"), eq(1L), anyString())).thenReturn(true);
        when(idempotencyStore.markCompleted(eq(TENANT_ID), eq("test-key"), eq(1L), anyString())).thenReturn(1);

        OrderView first = orderService.confirmOrder(1L, "test-key");
        assertEquals("CONFIRMED", first.status());

        OrderView retry = orderService.confirmOrder(1L, "test-key");
        assertEquals("CONFIRMED", retry.status());

        verify(orderRepository, times(1)).confirm(TENANT_ID, 1L, "APP_JAVA", null);
        verify(idempotencyStore, times(1)).markCompleted(eq(TENANT_ID), eq("test-key"), eq(1L), anyString());
    }

    @Test
    void shouldThrowWhenTenantContextNotSet() {
        when(tenantContext.getTenantId()).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> orderService.getOrder(1L));
        assertEquals("TENANT_CONTEXT_MISSING", ex.getCode());
        assertEquals(503, GlobalExceptionHandler.statusFor(ex.getCode()).value());
    }

    @Test
    void shouldGetOrder() {
        when(orderRepository.findById(TENANT_ID, 1L)).thenReturn(
                new OrderView(1L, "ORD-001", 100L, "DRAFT", "USD", null, 0, List.of()));

        OrderView result = orderService.getOrder(1L);

        assertEquals(1L, result.orderId());
        verify(orderRepository).findById(TENANT_ID, 1L);
    }

    @Test
    void shouldCancelOrder() {
        OrderView pendingOrder = new OrderView(1L, "ORD-001", 100L, "PENDING", "USD", null, 0, List.of());
        OrderView cancelledOrder = new OrderView(1L, "ORD-001", 100L, "CANCELLED", "USD", null, 0, List.of());
        when(orderRepository.findById(TENANT_ID, 1L)).thenReturn(pendingOrder).thenReturn(cancelledOrder);

        OrderView result = orderService.cancelOrder(1L, "Customer request", null);

        assertEquals("CANCELLED", result.status());
        verify(orderRepository).cancel(TENANT_ID, 1L, "Customer request", "APP_JAVA", null);
        verifyNoInteractions(idempotencyStore);
    }

    @Test
    void shouldCancelOrderIdempotently() throws Exception {
        OrderView pendingOrder = new OrderView(1L, "ORD-001", 100L, "PENDING", "USD", null, 0, List.of());
        OrderView cancelledOrder = new OrderView(1L, "ORD-001", 100L, "CANCELLED", "USD", null, 0, List.of());
        String payload = objectMapper.writeValueAsString(cancelledOrder);
        when(orderRepository.findById(TENANT_ID, 1L)).thenReturn(pendingOrder).thenReturn(cancelledOrder);
        when(idempotencyStore.findByKey(TENANT_ID, "cancel-key"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new IdempotencyStore.IdempotencyRecord(
                        1L, "CANCEL_ORDER", "COMPLETED", payload, null)));
        when(idempotencyStore.insertPending(eq(TENANT_ID), eq("cancel-key"), eq("CANCEL_ORDER"), eq(1L), anyString())).thenReturn(true);
        when(idempotencyStore.markCompleted(eq(TENANT_ID), eq("cancel-key"), eq(1L), anyString())).thenReturn(1);

        OrderView first = orderService.cancelOrder(1L, "Customer request", "cancel-key");
        OrderView retry = orderService.cancelOrder(1L, "Customer request", "cancel-key");

        assertEquals("CANCELLED", first.status());
        assertEquals("CANCELLED", retry.status());
        verify(orderRepository, times(1)).cancel(TENANT_ID, 1L, "Customer request", "APP_JAVA", null);
    }

    @Test
    void shouldRejectCancelWithoutReason() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.cancelOrder(1L, null, null));

        assertEquals("CANCEL_REASON_REQUIRED", ex.getCode());
        assertEquals(400, GlobalExceptionHandler.statusFor(ex.getCode()).value());
        verify(orderRepository, never()).cancel(anyLong(), anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void shouldRejectCancelWithBlankReason() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.cancelOrder(1L, "   ", null));

        assertEquals("CANCEL_REASON_REQUIRED", ex.getCode());
        verify(orderRepository, never()).cancel(anyLong(), anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void shouldConfirmOrderAndMarkIdempotency() {
        OrderView pendingOrder = new OrderView(1L, "ORD-001", 100L, "PENDING", "USD", null, 0, List.of());
        OrderView confirmedOrder = new OrderView(1L, "ORD-001", 100L, "CONFIRMED", "USD", null, 1, List.of());
        when(orderRepository.findById(TENANT_ID, 1L)).thenReturn(pendingOrder).thenReturn(confirmedOrder);
        when(idempotencyStore.findByKey(TENANT_ID, "idem-123")).thenReturn(Optional.empty());
        when(idempotencyStore.insertPending(eq(TENANT_ID), eq("idem-123"), eq("CONFIRM_ORDER"), eq(1L), anyString())).thenReturn(true);
        when(idempotencyStore.markCompleted(eq(TENANT_ID), eq("idem-123"), eq(1L), anyString())).thenReturn(1);

        OrderView result = orderService.confirmOrder(1L, "idem-123");

        assertEquals("CONFIRMED", result.status());
        verify(idempotencyStore).markCompleted(eq(TENANT_ID), eq("idem-123"), eq(1L), anyString());
    }

    @Test
    void shouldSkipIdempotencyWhenKeyIsNull() {
        OrderView pendingOrder = new OrderView(1L, "ORD-001", 100L, "PENDING", "USD", null, 0, List.of());
        OrderView confirmedOrder = new OrderView(1L, "ORD-001", 100L, "CONFIRMED", "USD", null, 1, List.of());
        when(orderRepository.findById(TENANT_ID, 1L)).thenReturn(pendingOrder).thenReturn(confirmedOrder);

        OrderView result = orderService.confirmOrder(1L, null);

        assertEquals("CONFIRMED", result.status());
        verify(idempotencyStore, never()).markCompleted(anyLong(), anyString(), eq(1L), anyString());
        verify(idempotencyStore, never()).findByKey(anyLong(), anyString());
    }

    @Test
    void shouldRejectAddLineWhenNotDraftOrPending() {
        OrderView confirmedOrder = new OrderView(1L, "ORD-001", 100L, "CONFIRMED", "USD", null, 1, List.of());
        when(orderRepository.findById(TENANT_ID, 1L)).thenReturn(confirmedOrder);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.addLine(1L, 10L, 5L));
        assertEquals("ORDER_NOT_DRAFT", ex.getCode());
        verify(orderRepository, never()).addLine(anyLong(), anyLong(), anyLong(), anyLong());
    }

    @Test
    void shouldRejectConfirmWhenNotPending() {
        OrderView confirmedOrder = new OrderView(1L, "ORD-001", 100L, "CONFIRMED", "USD", null, 1, List.of());
        when(orderRepository.findById(TENANT_ID, 1L)).thenReturn(confirmedOrder);
        when(idempotencyStore.findByKey(TENANT_ID, "key-123")).thenReturn(Optional.empty());
        when(idempotencyStore.insertPending(eq(TENANT_ID), eq("key-123"), eq("CONFIRM_ORDER"), eq(1L), anyString())).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.confirmOrder(1L, "key-123"));
        assertEquals("ORDER_NOT_CONFIRMABLE", ex.getCode());
        verify(orderRepository, never()).confirm(anyLong(), anyLong(), anyString(), anyString());
        verify(idempotencyStore, never()).markCompleted(anyLong(), anyString(), eq(1L), anyString());
    }

    @Test
    void shouldRejectCancelWhenShipped() {
        OrderView shippedOrder = new OrderView(1L, "ORD-001", 100L, "SHIPPED", "USD", null, 1, List.of());
        when(orderRepository.findById(TENANT_ID, 1L)).thenReturn(shippedOrder);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.cancelOrder(1L, "Too late", null));

        assertEquals("ORDER_SHIPPED_NOT_CANCELLABLE", ex.getCode());
        assertEquals(409, GlobalExceptionHandler.statusFor(ex.getCode()).value());
        verify(orderRepository, never()).cancel(anyLong(), anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void shouldRejectCancelWhenCompleted() {
        OrderView completedOrder = new OrderView(1L, "ORD-001", 100L, "COMPLETED", "USD", null, 0, List.of());
        when(orderRepository.findById(TENANT_ID, 1L)).thenReturn(completedOrder);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.cancelOrder(1L, "Too late", null));
        assertEquals("ORDER_NOT_CANCELLABLE", ex.getCode());
    }

    @Test
    void shouldRejectCancelWhenAlreadyCancelled() {
        OrderView cancelledOrder = new OrderView(1L, "ORD-001", 100L, "CANCELLED", "USD", null, 0, List.of());
        when(orderRepository.findById(TENANT_ID, 1L)).thenReturn(cancelledOrder);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.cancelOrder(1L, "Already cancelled", null));
        assertEquals("ORDER_NOT_CANCELLABLE", ex.getCode());
    }

    @Test
    void shouldRejectPaymentWhenDraft() {
        OrderView draftOrder = new OrderView(1L, "ORD-001", 100L, "DRAFT", "USD", null, 0, List.of());
        when(orderRepository.findById(TENANT_ID, 1L)).thenReturn(draftOrder);
        when(idempotencyStore.findByKey(TENANT_ID, "key-1")).thenReturn(Optional.empty());
        when(idempotencyStore.insertPending(eq(TENANT_ID), eq("key-1"), eq("PAYMENT"), eq(1L), anyString())).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.registerPayment(1L, "PAY-001", new java.math.BigDecimal("50.00"), "USD", "CARD", "key-1"));
        assertEquals("PAYMENT_STATE_INVALID", ex.getCode());
        verify(orderRepository, never()).registerPayment(anyLong(), anyLong(), anyString(), any(), anyString(), anyString(), anyString(), anyString());
        verify(idempotencyStore, never()).markCompleted(anyLong(), anyString(), eq(1L), anyString());
    }

    @Test
    void shouldRegisterPaymentIdempotently() throws Exception {
        OrderView confirmedOrder = new OrderView(1L, "ORD-001", 100L, "CONFIRMED", "USD", null, 1, List.of());
        OrderView paidOrder = new OrderView(1L, "ORD-001", 100L, "PAID", "USD", null, 1, List.of());
        String payload = objectMapper.writeValueAsString(paidOrder);
        when(orderRepository.findById(TENANT_ID, 1L)).thenReturn(confirmedOrder).thenReturn(paidOrder);
        when(idempotencyStore.findByKey(TENANT_ID, "pay-key")).thenReturn(Optional.empty());
        when(idempotencyStore.insertPending(eq(TENANT_ID), eq("pay-key"), eq("PAYMENT"), eq(1L), anyString()))
                .thenReturn(true);
        when(idempotencyStore.markCompleted(eq(TENANT_ID), eq("pay-key"), eq(1L), anyString())).thenReturn(1);

        OrderView first = orderService.registerPayment(1L, "PAY-001", new java.math.BigDecimal("50.00"),
                "USD", "CARD", "pay-key");

        // The retry must replay because it carries the same request
        // fingerprint that the first execution stored.
        var fpCaptor = ArgumentCaptor.forClass(String.class);
        verify(idempotencyStore).insertPending(eq(TENANT_ID), eq("pay-key"), eq("PAYMENT"), eq(1L),
                fpCaptor.capture());
        when(idempotencyStore.findByKey(TENANT_ID, "pay-key")).thenReturn(Optional.of(
                new IdempotencyStore.IdempotencyRecord(1L, "PAYMENT", "COMPLETED", payload,
                        fpCaptor.getValue())));

        OrderView retry = orderService.registerPayment(1L, "PAY-001", new java.math.BigDecimal("50.00"),
                "USD", "CARD", "pay-key");

        assertEquals("PAID", first.status());
        assertEquals("PAID", retry.status());
        verify(orderRepository, times(1)).registerPayment(eq(TENANT_ID), eq(1L), eq("PAY-001"),
                eq(new java.math.BigDecimal("50.00")), eq("USD"), eq("CARD"), eq("APP_JAVA"), isNull());
        verify(idempotencyStore, times(1)).markCompleted(eq(TENANT_ID), eq("pay-key"), eq(1L), anyString());
    }

    @Test
    void shouldRejectPaymentWhenKeyReusedWithDifferentParams() throws Exception {
        // Same key, same operation, same order, but a DIFFERENT request
        // (closure fix C4): must 409, never silently replay the first
        // payment's response as if the second one had been executed.
        OrderView paidOrder = new OrderView(1L, "ORD-001", 100L, "PAID", "USD", null, 1, List.of());
        String payload = objectMapper.writeValueAsString(paidOrder);
        when(idempotencyStore.findByKey(TENANT_ID, "pay-shared")).thenReturn(Optional.of(
                new IdempotencyStore.IdempotencyRecord(1L, "PAYMENT", "COMPLETED", payload,
                        "fingerprint-of-a-different-request")));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.registerPayment(1L, "PAY-002", new java.math.BigDecimal("999.00"),
                        "USD", "CARD", "pay-shared"));

        assertEquals("IDEMPOTENCY_KEY_CONFLICT", ex.getCode());
        assertEquals(409, GlobalExceptionHandler.statusFor(ex.getCode()).value());
        verify(orderRepository, never()).registerPayment(anyLong(), anyLong(), anyString(), any(),
                anyString(), anyString(), anyString(), anyString());
    }
}
