package com.enterpriseorders.backend.common.domain;

import java.util.Set;

public enum PaymentMethod {
    CARD,
    BANK_TRANSFER,
    WALLET,
    CASH;

    private static final Set<String> VALID_METHODS = Set.of("CARD", "BANK_TRANSFER", "WALLET", "CASH");

    public static PaymentMethod fromString(String method) {
        if (!VALID_METHODS.contains(method)) {
            throw new IllegalArgumentException("Invalid payment method: " + method);
        }
        return valueOf(method);
    }
}
