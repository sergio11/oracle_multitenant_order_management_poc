package com.enterpriseorders.backend.order.port.input;

import com.enterpriseorders.backend.order.model.OrderView;

public interface CreateOrderUseCase {
    OrderView createOrder(Long customerId, String currency, String idempotencyKey);
}
