package com.enterpriseorders.backend.order.dto;

import com.enterpriseorders.backend.common.domain.Currency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateOrderRequestDTO(
        @NotNull Long customerId,
        @NotBlank String currency
) {
    public CreateOrderRequestDTO {
        Currency.of(currency);
    }
}
