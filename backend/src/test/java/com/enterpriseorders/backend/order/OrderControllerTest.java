package com.enterpriseorders.backend.order;

import com.enterpriseorders.backend.order.dto.OrderResponse;
import com.enterpriseorders.backend.order.port.input.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
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

    private static final String BASE = "/api/v1/orders";
    private final OrderResponse mockOrder = new OrderResponse(1L, "ORD-001", 1L, 100L, "DRAFT", "USD", BigDecimal.ZERO, 0);

    @Test
    void shouldCreateOrder() throws Exception {
        when(createOrderUseCase.createOrder(100L, "USD")).thenReturn(mockOrder);
        mockMvc.perform(post(BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\":100,\"currency\":\"USD\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(1));
    }

    @Test
    void shouldGetOrder() throws Exception {
        when(getOrderQuery.getOrder(1L)).thenReturn(mockOrder);
        mockMvc.perform(get(BASE + "/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber").value("ORD-001"));
    }

    @Test
    void shouldConfirmOrder() throws Exception {
        var confirmed = new OrderResponse(1L, "ORD-001", 1L, 100L, "CONFIRMED", "USD", BigDecimal.ZERO, 1);
        when(confirmOrderUseCase.confirmOrder(1L, "key-123")).thenReturn(confirmed);
        mockMvc.perform(post(BASE + "/1/confirm")
                .header("Idempotency-Key", "key-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void shouldCancelOrderWithReason() throws Exception {
        var cancelled = new OrderResponse(1L, "ORD-001", 1L, 100L, "CANCELLED", "USD", BigDecimal.ZERO, 1);
        when(cancelOrderUseCase.cancelOrder(1L, "Customer request")).thenReturn(cancelled);
        mockMvc.perform(post(BASE + "/1/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Customer request\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void shouldCancelOrderWithoutBody() throws Exception {
        var cancelled = new OrderResponse(1L, "ORD-001", 1L, 100L, "CANCELLED", "USD", BigDecimal.ZERO, 1);
        when(cancelOrderUseCase.cancelOrder(1L, null)).thenReturn(cancelled);
        mockMvc.perform(post(BASE + "/1/cancel"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRegisterPayment() throws Exception {
        var paid = new OrderResponse(1L, "ORD-001", 1L, 100L, "PAID", "USD", BigDecimal.valueOf(50), 2);
        when(registerPaymentUseCase.registerPayment(eq(1L), eq("PAY-001"), any(), eq("USD"), eq("CARD"), isNull()))
                .thenReturn(paid);
        mockMvc.perform(post(BASE + "/1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentReference\":\"PAY-001\",\"amount\":50.00,\"currency\":\"USD\",\"paymentMethod\":\"CARD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void shouldReturn400WhenInvalidCreateOrderRequest() throws Exception {
        mockMvc.perform(post(BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currency\":\"USD\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldAddLine() throws Exception {
        when(addOrderLineUseCase.addLine(1L, 10L, 5L, 20L)).thenReturn(mockOrder);
        mockMvc.perform(post(BASE + "/1/lines")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":10,\"quantity\":5,\"warehouseId\":20}"))
                .andExpect(status().isOk());
    }
}
