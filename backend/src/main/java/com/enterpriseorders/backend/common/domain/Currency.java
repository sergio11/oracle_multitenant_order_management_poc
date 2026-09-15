package com.enterpriseorders.backend.common.domain;

import java.util.Objects;
import java.util.Set;

public record Currency(String code) {
    private static final Set<String> VALID_CODES = Set.of("USD", "EUR", "GBP", "JPY", "ARS", "CAD", "AUD");

    public Currency {
        Objects.requireNonNull(code, "Currency code must not be null");
        if (!VALID_CODES.contains(code)) {
            throw new IllegalArgumentException("Invalid currency code: " + code);
        }
    }

    public static Currency of(String code) {
        return new Currency(code);
    }
}
