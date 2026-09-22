package com.enterpriseorders.backend.order.domain;

import com.enterpriseorders.backend.common.domain.Money;
import com.enterpriseorders.backend.common.domain.OrderStatus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Order aggregate (Java-layer model).
 *
 * <p>The authoritative lifecycle/state machine lives in PL/SQL
 * ({@code pkg_order_management}); mutations are atomic in the database.
 * This aggregate models the same rules for the Java layer and documents
 * the state machine, but it is not instantiated in the current request
 * flow because mutations are atomic in the database.</p>
 */
public class Order {

    private final Long orderId;
    private final String orderNumber;
    private final Long tenantId;
    private final Long customerId;
    private OrderStatus status;
    private final Money totalAmount;
    private final List<OrderLine> lines;
    private final int version;

    public Order(Long orderId, String orderNumber, Long tenantId, Long customerId,
                 OrderStatus status, BigDecimal totalAmount, String currency, int version) {
        this.orderId = Objects.requireNonNull(orderId, "Order ID must not be null");
        this.orderNumber = Objects.requireNonNull(orderNumber, "Order number must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        this.customerId = Objects.requireNonNull(customerId, "Customer ID must not be null");
        this.status = Objects.requireNonNull(status, "Status must not be null");
        this.totalAmount = totalAmount != null ? new Money(totalAmount, com.enterpriseorders.backend.common.domain.Currency.of(currency))
                                               : Money.of(BigDecimal.ZERO, currency);
        this.lines = new ArrayList<>();
        this.version = version;
    }

    public Long getOrderId() { return orderId; }
    public String getOrderNumber() { return orderNumber; }
    public Long getTenantId() { return tenantId; }
    public Long getCustomerId() { return customerId; }
    public OrderStatus getStatus() { return status; }
    public Money getTotalAmount() { return totalAmount; }
    public List<OrderLine> getLines() { return Collections.unmodifiableList(lines); }
    public int getVersion() { return version; }

    public boolean canAddLines() {
        return this.status == OrderStatus.DRAFT;
    }

    public boolean canConfirm() {
        return this.status == OrderStatus.PENDING && !lines.isEmpty();
    }

    public boolean canCancel() {
        return this.status != OrderStatus.COMPLETED && this.status != OrderStatus.CANCELLED;
    }

    public boolean canAcceptPayment() {
        return this.status == OrderStatus.CONFIRMED
            || this.status == OrderStatus.ALLOCATED
            || this.status == OrderStatus.PAID;
    }

    public void addLine(OrderLine line) {
        if (!canAddLines()) {
            throw new IllegalStateException("Cannot add lines to a non-DRAFT order");
        }
        this.lines.add(line);
    }

    /**
     * Computes the total from order lines for Java-side validation.
     * The canonical total is maintained by PL/SQL in the database.
     */
    public Money calculateTotal() {
        return lines.stream()
            .map(OrderLine::lineTotal)
            .reduce(Money::add)
            .orElse(Money.of(BigDecimal.ZERO, totalAmount.currency().code()));
    }
}
