package com.enterpriseorders.backend.reporting.port.input;

import com.enterpriseorders.backend.reporting.port.output.dto.ProductSalesSummary;

import java.util.List;

public interface GetTopProductsQuery {
    List<ProductSalesSummary> getTopProducts(int limit);
}
