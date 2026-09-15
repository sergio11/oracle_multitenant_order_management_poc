package com.enterpriseorders.backend.reporting.port.input;

import com.enterpriseorders.backend.reporting.ReportingDao.OrderDailySummary;

import java.util.List;

public interface GetDailyOrdersQuery {
    List<OrderDailySummary> getDailyOrders();
}
