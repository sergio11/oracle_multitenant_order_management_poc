package com.enterpriseorders.backend.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request payload for creating an order (SRS §53, FR-007).
 *
 * customerId must belong to the same tenant (BR-002), enforced by the
 * PL/SQL package pkg_order_management.create_order.
 */
public record CreateOrderRequest(
        @NotNull Long customerId,
        @NotBlank String currency
) {
}
</content>
<task_progress>
- [x] FASE 7: OrderDao + OrderLineRequest
- [x] FASE 7: CreateOrderRequest DTO
- [ ] FASE 7: OrderResponse DTO
- [ ] FASE 7: Migración V012 (tabla idempotency_key para FR-014/SRS §62)
- [ ] FASE 7: OrderService (con idempotencia)
- [ ] FASE 7: OrderController (REST endpoints)
- [ ] FASE 7: Commit fase 7
- [ ] FASE 8 a 12 (resto del plan)
</task_progress>