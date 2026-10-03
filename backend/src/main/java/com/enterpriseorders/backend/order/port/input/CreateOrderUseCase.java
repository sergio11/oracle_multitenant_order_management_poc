package com.enterpriseorders.backend.order.port.input;

import com.enterpriseorders.backend.order.model.OrderBO;

public interface CreateOrderUseCase {
    OrderBO createOrder(Long customerId, String currency, String idempotencyKey);
}
