package com.enterpriseorders.backend.reporting.port.output;

import com.enterpriseorders.backend.reporting.port.output.dto.OrderDailySummary;
import com.enterpriseorders.backend.reporting.port.output.dto.ProductSalesSummary;

import java.util.List;

public interface ReportingQueryPort {
    List<OrderDailySummary> getDailyOrders();
    List<ProductSalesSummary> getTopProducts(int limit);
}
