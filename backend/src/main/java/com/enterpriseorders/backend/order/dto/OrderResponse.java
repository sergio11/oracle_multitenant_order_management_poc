package com.enterpriseorders.backend.order.dto;

import java.math.BigDecimal;

/**
 * Response payload for order operations (SRS §53).
 */
public record OrderResponse(
        Long orderId,
        String orderNumber,
        Long tenantId,
        Long customerId,
        String status,
        String currency,
        BigDecimal totalAmount
) {
}
</content>
<task_progress>
- [x] FASE 7: OrderDao + DTOs (OrderLineRequest, CreateOrderRequest)
- [x] FASE 7: OrderResponse DTO
- [ ] FASE 7: Migración V012 (tabla idempotency_key para FR-014/SRS §62)
- [ ] FASE 7: OrderService (con idempotencia)
- [ ] FASE 7: OrderController (REST endpoints)
- [ ] FASE 7: Commit fase 7
- [ ] FASE 8 a 12 (resto del plan)
</task_progress>