package com.enterpriseorders.backend.order;

import com.enterpriseorders.backend.exception.BusinessException;
import com.enterpriseorders.backend.order.dto.OrderResponse;
import com.enterpriseorders.backend.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
    private OrderDao orderDao;

    @Mock
    private IdempotencyDao idempotencyDao;

    @Mock
    private OutboxDao outboxDao;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private OrderService orderService;

    private static final Long TENANT_ID = 1L;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldCreateOrder() {
        when(orderDao.createOrder(TENANT_ID, 100L, "USD")).thenReturn(1L);
        when(orderDao.findOrder(TENANT_ID, 1L)).thenReturn(
                new OrderResponse(1L, "ORD-001", TENANT_ID, 100L, "DRAFT", "USD", null));

        OrderResponse result = orderService.createOrder(100L, "USD");

        assertEquals(1L, result.orderId());
        assertEquals("DRAFT", result.status());
        verify(orderDao).createOrder(TENANT_ID, 100L, "USD");
        verify(orderDao).findOrder(TENANT_ID, 1L);
    }

    @Test
    void shouldAddLineToOrder() {
        when(orderDao.findOrder(TENANT_ID, 1L)).thenReturn(
                new OrderResponse(1L, "ORD-001", TENANT_ID, 100L, "DRAFT", "USD", null));

        OrderResponse result = orderService.addLine(1L, 10L, 5L, 20L);

        assertNotNull(result);
        verify(orderDao).addOrderLine(TENANT_ID, 1L, 10L, 5L, 20L);
    }

    @Test
    void shouldReturnExistingOrderOnIdempotentRetry() {
        var idempotencyRecord = new IdempotencyDao.IdempotencyRecord(1L, "CONFIRM_ORDER", "COMPLETED");
        when(idempotencyDao.findByKey(TENANT_ID, "test-key")).thenReturn(Optional.of(idempotencyRecord));
        when(orderDao.findOrder(TENANT_ID, 1L)).thenReturn(
                new OrderResponse(1L, "ORD-001", TENANT_ID, 100L, "CONFIRMED", "USD", null));

        OrderResponse result = orderService.confirmOrder(1L, "test-key");

        assertEquals("CONFIRMED", result.status());
        verify(orderDao).findOrder(TENANT_ID, 1L);
        verify(orderDao, never()).confirmOrder(anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    void shouldThrowWhenTenantContextNotSet() {
        TenantContext.clear();
        assertThrows(IllegalStateException.class, () -> orderService.getOrder(1L));
    }

    @Test
    void shouldGetOrder() {
        when(orderDao.findOrder(TENANT_ID, 1L)).thenReturn(
                new OrderResponse(1L, "ORD-001", TENANT_ID, 100L, "DRAFT", "USD", null));

        OrderResponse result = orderService.getOrder(1L);

        assertEquals(1L, result.orderId());
        verify(orderDao).findOrder(TENANT_ID, 1L);
    }

    @Test
    void shouldCancelOrder() throws Exception {
        when(orderDao.findOrder(TENANT_ID, 1L)).thenReturn(
                new OrderResponse(1L, "ORD-001", TENANT_ID, 100L, "CANCELLED", "USD", null));

        OrderResponse result = orderService.cancelOrder(1L, "Customer request");

        assertEquals("CANCELLED", result.status());
        verify(orderDao).cancelOrder(TENANT_ID, 1L, "Customer request", "APP_JAVA", null);
        verify(outboxDao).writeEvent(eq(TENANT_ID), eq("ORDER"), eq("1"), eq("ORDER_CANCELLED"), anyString());
    }

    @Test
    void shouldConfirmOrderAndPublishOutboxEvent() throws Exception {
        when(orderDao.findOrder(TENANT_ID, 1L)).thenReturn(
                new OrderResponse(1L, "ORD-001", TENANT_ID, 100L, "CONFIRMED", "USD", null));

        OrderResponse result = orderService.confirmOrder(1L, "idem-123");

        assertEquals("CONFIRMED", result.status());
        verify(orderDao).confirmOrder(TENANT_ID, 1L, "APP_JAVA", null);
        verify(idempotencyDao).markCompleted(eq(TENANT_ID), eq("idem-123"), anyString());
        verify(outboxDao).writeEvent(eq(TENANT_ID), eq("ORDER"), eq("1"), eq("ORDER_CONFIRMED"), anyString());
    }

    @Test
    void shouldSkipIdempotencyMarkWhenKeyIsNull() throws Exception {
        when(orderDao.findOrder(TENANT_ID, 1L)).thenReturn(
                new OrderResponse(1L, "ORD-001", TENANT_ID, 100L, "CONFIRMED", "USD", null));

        orderService.confirmOrder(1L, null);

        verify(idempotencyDao, never()).markCompleted(anyLong(), anyString(), anyString());
    }
}
