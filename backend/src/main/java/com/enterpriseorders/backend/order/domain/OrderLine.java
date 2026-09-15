package com.enterpriseorders.backend.order.domain;

import com.enterpriseorders.backend.common.domain.Money;
import com.enterpriseorders.backend.common.domain.Quantity;

import java.math.BigDecimal;
import java.util.Objects;

public record OrderLine(
    Long lineId,
    Long productId,
    Quantity quantity,
    Money unitPrice,
    Money lineTotal
) {
    public OrderLine {
        Objects.requireNonNull(productId, "Product ID must not be null");
        Objects.requireNonNull(quantity, "Quantity must not be null");
        Objects.requireNonNull(unitPrice, "Unit price must not be null");
        Objects.requireNonNull(lineTotal, "Line total must not be null");
    }

    public static OrderLine create(Long productId, long quantity, BigDecimal unitPrice, String currency) {
        Quantity qty = Quantity.of(quantity);
        Money price = Money.of(unitPrice, currency);
        Money total = Money.of(unitPrice.multiply(BigDecimal.valueOf(quantity)), currency);
        return new OrderLine(null, productId, qty, price, total);
    }
}
