package com.enterpriseorders.backend.reporting.port.input;

import com.enterpriseorders.backend.reporting.port.output.dto.ReportsFreshness;

/**
 * Read-only freshness of the reporting materialized views, so consumers can
 * tell how stale report data is without changing existing report payloads.
 */
public interface GetReportsFreshnessQuery {
    ReportsFreshness getReportsFreshness();
}
