package com.enterpriseorders.backend.order.dto;

import com.enterpriseorders.backend.common.domain.Currency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateOrderRequest(
        @NotNull Long customerId,
        @NotBlank String currency
) {
    public CreateOrderRequest {
        Currency.of(currency);
    }
}
