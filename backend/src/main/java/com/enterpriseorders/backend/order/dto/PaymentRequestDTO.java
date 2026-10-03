package com.enterpriseorders.backend.order.dto;

import com.enterpriseorders.backend.common.domain.Currency;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PaymentRequestDTO(
        @NotBlank @Size(max = 100) String paymentReference,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
        @NotBlank @Pattern(regexp = "CARD|BANK_TRANSFER|WALLET|CASH") String paymentMethod
) {
    public PaymentRequestDTO {
        Currency.of(currency);
    }
}
