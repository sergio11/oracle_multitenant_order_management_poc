package com.enterpriseorders.backend.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request payload for adding a line to an order (SRS ��53).
 */
public record OrderLineRequestDTO(
        @NotNull Long productId,
        @NotNull @Min(1) @Max(999999999999L) Long quantity
) {
}
