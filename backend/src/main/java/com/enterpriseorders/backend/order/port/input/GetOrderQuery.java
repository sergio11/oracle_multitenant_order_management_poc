package com.enterpriseorders.backend.order.port.input;

import com.enterpriseorders.backend.order.dto.OrderResponse;

public interface GetOrderQuery {
    OrderResponse getOrder(Long orderId);
}
