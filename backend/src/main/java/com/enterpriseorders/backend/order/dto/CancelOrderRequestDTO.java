package com.enterpriseorders.backend.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelOrderRequestDTO(
        @NotBlank @Size(max = 500) String reason
) {
}
