package com.enterpriseorders.backend.common.domain;

import java.util.Objects;

public record Quantity(long value) {
    public Quantity {
        if (value <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
    }

    public static Quantity of(long value) {
        return new Quantity(value);
    }

    public Quantity add(Quantity other) {
        return new Quantity(this.value + other.value);
    }

    public Quantity subtract(Quantity other) {
        if (this.value < other.value) {
            throw new IllegalArgumentException("Cannot subtract more than available");
        }
        return new Quantity(this.value - other.value);
    }
}
