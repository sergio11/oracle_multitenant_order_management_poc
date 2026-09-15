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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private IdempotentOperationExecutor idempotentExecutor;

    @Mock
    private IdempotencyStore idempotencyStore;

    @Mock
    private TenantContextProvider tenantContext;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private OrderService orderService;

    private static final Long TENANT_ID = 1L;

    @BeforeEach
    void setUp() {
        when(tenantContext.getTenantId()).thenReturn(TENANT_ID);
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
    void shouldReturnExistingOrderOnIdempotentRetry() {
        OrderResponse existingOrder = new OrderResponse(1L, "ORD-001", TENANT_ID, 100L, "CONFIRMED", "USD", null, 0);
        when(idempotentExecutor.execute(eq(TENANT_ID), eq("test-key"), eq("CONFIRM_ORDER"), eq(1L), any()))
                .thenReturn(existingOrder);

        OrderResponse result = orderService.confirmOrder(1L, "test-key");

        assertEquals("CONFIRMED", result.status());
        verify(idempotentExecutor).execute(eq(TENANT_ID), eq("test-key"), eq("CONFIRM_ORDER"), eq(1L), any());
        verify(orderRepository, never()).confirm(anyLong(), anyLong(), anyString(), anyString());
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
        when(orderRepository.findById(TENANT_ID, 1L)).thenReturn(
                new OrderResponse(1L, "ORD-001", TENANT_ID, 100L, "CANCELLED", "USD", null, 0));

        OrderResponse result = orderService.cancelOrder(1L, "Customer request");

        assertEquals("CANCELLED", result.status());
        verify(orderRepository).cancel(TENANT_ID, 1L, "Customer request", "APP_JAVA", null);
    }

    @Test
    void shouldConfirmOrderAndMarkIdempotency() {
        OrderResponse confirmedOrder = new OrderResponse(1L, "ORD-001", TENANT_ID, 100L, "CONFIRMED", "USD", null, 0);
        doNothing().when(orderRepository).confirm(eq(TENANT_ID), eq(1L), anyString(), any());
        when(orderRepository.findById(TENANT_ID, 1L)).thenReturn(confirmedOrder);
        when(idempotentExecutor.execute(eq(TENANT_ID), eq("idem-123"), eq("CONFIRM_ORDER"), eq(1L), any()))
                .thenAnswer(invocation -> {
                    java.util.function.Supplier<OrderResponse> action = invocation.getArgument(4);
                    return action.get();
                });

        OrderResponse result = orderService.confirmOrder(1L, "idem-123");

        assertEquals("CONFIRMED", result.status());
        verify(idempotentExecutor).execute(eq(TENANT_ID), eq("idem-123"), eq("CONFIRM_ORDER"), eq(1L), any());
        verify(idempotencyStore).markCompleted(eq(TENANT_ID), eq("idem-123"), anyString());
    }

    @Test
    void shouldSkipIdempotencyMarkWhenKeyIsNull() {
        OrderResponse confirmedOrder = new OrderResponse(1L, "ORD-001", TENANT_ID, 100L, "CONFIRMED", "USD", null, 0);
        when(idempotentExecutor.execute(eq(TENANT_ID), isNull(), eq("CONFIRM_ORDER"), eq(1L), any()))
                .thenReturn(confirmedOrder);

        orderService.confirmOrder(1L, null);

        verify(idempotencyStore, never()).markCompleted(anyLong(), anyString(), anyString());
    }
}
