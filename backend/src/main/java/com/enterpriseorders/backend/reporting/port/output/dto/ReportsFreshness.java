package com.enterpriseorders.backend.reporting.port.output.dto;

import java.time.Instant;

/**
 * Freshness signal for the reporting materialized views.
 *
 * @param lastRefresh latest refresh timestamp of the reporting MVs
 *                    ({@code null} when never refreshed / unknown)
 * @param source      where the signal comes from (data dictionary)
 */
public record ReportsFreshness(
        Instant lastRefresh,
        String source
) {
}
