package com.enterpriseorders.backend.reporting.port.output.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record OrderDailySummary(
        Long tenantId,
        LocalDate orderDate,
        Long orderCount,
        BigDecimal totalRevenue,
        BigDecimal averageOrderValue
) {
}
