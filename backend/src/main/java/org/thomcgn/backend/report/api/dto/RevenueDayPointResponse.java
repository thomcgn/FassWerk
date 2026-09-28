package org.thomcgn.backend.report.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Revenue Day Point Response contract")
public record RevenueDayPointResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String label,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal revenue
) {
}

