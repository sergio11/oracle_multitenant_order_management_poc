package com.enterpriseorders.backend.order.port.input;

import com.enterpriseorders.backend.order.model.OrderView;

public interface GetOrderQuery {
    OrderView getOrder(Long orderId);
}
