package com.enterpriseorders.backend.reporting.port.output.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProductSalesSummary(
        Long tenantId,
        Long productId,
        LocalDate period,
        Long quantitySold,
        BigDecimal revenue,
        Long orderCount
) {
}
