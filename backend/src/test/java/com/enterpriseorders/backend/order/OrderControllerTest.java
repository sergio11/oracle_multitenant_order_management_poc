package com.enterpriseorders.backend.order;

import com.enterpriseorders.backend.exception.BusinessException;
import com.enterpriseorders.backend.order.adapter.web.OrderController;
import com.enterpriseorders.backend.order.adapter.web.mapper.OrderMapperImpl;
import com.enterpriseorders.backend.order.model.OrderBO;
import com.enterpriseorders.backend.order.port.input.*;
import com.enterpriseorders.backend.tenant.port.input.ResolveTenantUseCase;
import com.enterpriseorders.backend.tenant.port.output.TenantContextHolder;
import com.enterpriseorders.backend.tenant.port.output.RoleTenancy;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
@Import(OrderMapperImpl.class)
@AutoConfigureMockMvc(addFilters = false)
class OrderControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private CreateOrderUseCase createOrderUseCase;
    @MockBean private AddOrderLineUseCase addOrderLineUseCase;
    @MockBean private ConfirmOrderUseCase confirmOrderUseCase;
    @MockBean private CancelOrderUseCase cancelOrderUseCase;
    @MockBean private RegisterPaymentUseCase registerPaymentUseCase;
    @MockBean private GetOrderQuery getOrderQuery;
    @MockBean private ResolveTenantUseCase resolveTenantUseCase;
    @MockBean private TenantContextHolder tenantContextHolder;
    @MockBean private RoleTenancy roleTenancy;

    private static final String BASE = "/api/v1/orders";
    private final OrderBO mockOrder = new OrderBO(1L, "ORD-001", 100L, "DRAFT", "USD", BigDecimal.ZERO, 0, List.of());

    @Test
    void shouldCreateOrder() throws Exception {
        when(createOrderUseCase.createOrder(100L, "USD", null)).thenReturn(mockOrder);
        mockMvc.perform(post(BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\":100,\"currency\":\"USD\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(1))
                .andExpect(header().string("Location", "/api/v1/orders/1"));
    }

    @Test
    void shouldCreateOrderWithIdempotencyKey() throws Exception {
        when(createOrderUseCase.createOrder(100L, "USD", "key-1")).thenReturn(mockOrder);
        mockMvc.perform(post(BASE)
                .header("Idempotency-Key", "key-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\":100,\"currency\":\"USD\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(1));
        verify(createOrderUseCase).createOrder(100L, "USD", "key-1");
    }

    @Test
    void shouldGetOrder() throws Exception {
        when(getOrderQuery.getOrder(1L)).thenReturn(mockOrder);
        mockMvc.perform(get(BASE + "/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber").value("ORD-001"));
    }

    @Test
    void shouldGetOrderWithLines() throws Exception {
        var withLines = new OrderBO(1L, "ORD-001", 100L, "PENDING", "USD", BigDecimal.valueOf(10), 1,
                List.of(new OrderBO.OrderLineBO(10L, 5L, BigDecimal.valueOf(2), BigDecimal.valueOf(10))));
        when(getOrderQuery.getOrder(1L)).thenReturn(withLines);
        mockMvc.perform(get(BASE + "/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines.length()").value(1))
                .andExpect(jsonPath("$.lines[0].productId").value(10))
                .andExpect(jsonPath("$.lines[0].quantity").value(5));
    }

    @Test
    void shouldConfirmOrder() throws Exception {
        var confirmed = new OrderBO(1L, "ORD-001", 100L, "CONFIRMED", "USD", BigDecimal.ZERO, 1, List.of());
        when(confirmOrderUseCase.confirmOrder(1L, "key-123")).thenReturn(confirmed);
        mockMvc.perform(post(BASE + "/1/confirm")
                .header("Idempotency-Key", "key-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void shouldCancelOrderWithReason() throws Exception {
        var cancelled = new OrderBO(1L, "ORD-001", 100L, "CANCELLED", "USD", BigDecimal.ZERO, 1, List.of());
        when(cancelOrderUseCase.cancelOrder(1L, "Customer request", null)).thenReturn(cancelled);
        mockMvc.perform(post(BASE + "/1/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Customer request\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void shouldCancelOrderWithIdempotencyKey() throws Exception {
        var cancelled = new OrderBO(1L, "ORD-001", 100L, "CANCELLED", "USD", BigDecimal.ZERO, 1, List.of());
        when(cancelOrderUseCase.cancelOrder(1L, "Customer request", "key-c")).thenReturn(cancelled);
        mockMvc.perform(post(BASE + "/1/cancel")
                .header("Idempotency-Key", "key-c")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Customer request\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        verify(cancelOrderUseCase).cancelOrder(1L, "Customer request", "key-c");
    }

    @Test
    void shouldRejectCancelWithoutReason() throws Exception {
        when(cancelOrderUseCase.cancelOrder(1L, null, null))
                .thenThrow(new BusinessException("CANCEL_REASON_REQUIRED",
                        "Cancellation reason is required"));
        mockMvc.perform(post(BASE + "/1/cancel"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CANCEL_REASON_REQUIRED"));
    }

    @Test
    void shouldRejectCancelWithReasonLongerThan500() throws Exception {
        mockMvc.perform(post(BASE + "/1/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"" + "x".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verify(cancelOrderUseCase, never()).cancelOrder(anyLong(), anyString(), anyString());
    }

    @Test
    void shouldRegisterPayment() throws Exception {
        var paid = new OrderBO(1L, "ORD-001", 100L, "PAID", "USD", BigDecimal.valueOf(50), 2, List.of());
        when(registerPaymentUseCase.registerPayment(eq(1L), eq("PAY-001"), any(), eq("USD"), eq("CARD"), isNull()))
                .thenReturn(paid);
        mockMvc.perform(post(BASE + "/1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentReference\":\"PAY-001\",\"amount\":50.00,\"currency\":\"USD\",\"paymentMethod\":\"CARD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void shouldRejectPaymentReferenceLongerThan100() throws Exception {
        mockMvc.perform(post(BASE + "/1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentReference\":\"" + "p".repeat(101) + "\",\"amount\":50.00,\"currency\":\"USD\",\"paymentMethod\":\"CARD\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verify(registerPaymentUseCase, never()).registerPayment(anyLong(), anyString(), any(), anyString(), anyString(), anyString());
    }

    @Test
    void shouldReturn400WhenInvalidCreateOrderRequestDTO() throws Exception {
        mockMvc.perform(post(BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currency\":\"USD\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldAddLine() throws Exception {
        when(addOrderLineUseCase.addLine(1L, 10L, 5L)).thenReturn(mockOrder);
        mockMvc.perform(post(BASE + "/1/lines")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":10,\"quantity\":5}"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectAddLineWithOversizedQuantity() throws Exception {
        mockMvc.perform(post(BASE + "/1/lines")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":10,\"quantity\":1000000000000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verify(addOrderLineUseCase, never()).addLine(anyLong(), anyLong(), anyLong());
    }
}
