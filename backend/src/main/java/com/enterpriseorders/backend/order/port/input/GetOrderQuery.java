package com.enterpriseorders.backend.order.port.input;

import com.enterpriseorders.backend.order.model.OrderBO;

public interface GetOrderQuery {
    OrderBO getOrder(Long orderId);
}
