package com.enterpriseorders.backend.order.port.input;

import com.enterpriseorders.backend.order.dto.OrderResponse;

import java.math.BigDecimal;

public interface RegisterPaymentUseCase {
    OrderResponse registerPayment(Long orderId, String paymentRef, BigDecimal amount,
                                   String currency, String paymentMethod, String idempotencyKey);
}
