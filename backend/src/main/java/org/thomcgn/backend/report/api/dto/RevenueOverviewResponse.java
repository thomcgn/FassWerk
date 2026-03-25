package org.thomcgn.backend.report.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record RevenueOverviewResponse(
        BigDecimal dayRevenue,
        BigDecimal weekRevenue,
        BigDecimal monthRevenue,
        BigDecimal dayConsumedMl,
        BigDecimal weekConsumedMl,
        BigDecimal monthConsumedMl,
        String strongestWeekday,
        List<RevenueDayPointResponse> weekPoints,
        List<RevenueDayPointResponse> monthPoints
) {
}

