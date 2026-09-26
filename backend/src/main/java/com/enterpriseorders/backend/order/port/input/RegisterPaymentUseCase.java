package com.enterpriseorders.backend.order.port.input;

import com.enterpriseorders.backend.order.model.OrderView;

import java.math.BigDecimal;

public interface RegisterPaymentUseCase {
    OrderView registerPayment(Long orderId, String paymentRef, BigDecimal amount,
                                   String currency, String paymentMethod, String idempotencyKey);
}
