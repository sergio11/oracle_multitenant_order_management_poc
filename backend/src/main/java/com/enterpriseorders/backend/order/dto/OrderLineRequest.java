package com.enterpriseorders.backend.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request payload for adding a line to an order (SRS §53).
 */
public record OrderLineRequest(
        @NotNull Long productId,
        @NotNull @Min(1) Long quantity,
        @NotNull Long warehouseId
) {
}
</content>
<task_progress>
- [x] FASE 7: OrderDao (invoca PL/SQL packages vía JDBC + mapeo errores)
- [x] FASE 7: OrderLineRequest DTO
- [ ] FASE 7: CreateOrderRequest + OrderResponse DTOs
- [ ] FASE 7: Migración V012 (tabla idempotency_key para FR-014/SRS §62)
- [ ] FASE 7: OrderService (con idempotencia)
- [ ] FASE 7: OrderController (REST endpoints)
- [ ] FASE 7: Commit fase 7
- [ ] FASE 8 a 12 (resto del plan)
</task_progress>