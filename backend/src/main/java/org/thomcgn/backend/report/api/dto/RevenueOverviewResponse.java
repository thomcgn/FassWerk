package org.thomcgn.backend.report.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Revenue Overview Response contract")
public record RevenueOverviewResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal dayRevenue,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal weekRevenue,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal monthRevenue,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal dayConsumedMl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal weekConsumedMl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal monthConsumedMl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String strongestWeekday,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<RevenueDayPointResponse> weekPoints,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<RevenueDayPointResponse> monthPoints
) {
}

