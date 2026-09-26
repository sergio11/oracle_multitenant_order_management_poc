package com.enterpriseorders.backend.order.port.output;

import com.enterpriseorders.backend.order.model.OrderView;

import java.math.BigDecimal;

public interface OrderRepository {
    OrderView findById(Long tenantId, Long orderId);
    Long create(Long tenantId, Long customerId, String currency);
    void addLine(Long tenantId, Long orderId, Long productId, Long quantity);
    void confirm(Long tenantId, Long orderId, String actor, String correlationId);
    void cancel(Long tenantId, Long orderId, String reason, String actor, String correlationId);
    void registerPayment(Long tenantId, Long orderId, String paymentRef,
                         BigDecimal amount, String currency, String paymentMethod,
                         String actor, String correlationId);
}
