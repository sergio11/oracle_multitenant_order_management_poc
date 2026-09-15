package com.enterpriseorders.backend.order.port.input;

import com.enterpriseorders.backend.order.dto.OrderResponse;

public interface CreateOrderUseCase {
    OrderResponse createOrder(Long customerId, String currency);
}
