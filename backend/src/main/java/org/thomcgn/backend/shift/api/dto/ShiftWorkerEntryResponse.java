package org.thomcgn.backend.shift.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalTime;

@Schema(description = "Shift Worker Entry Response contract")
public record ShiftWorkerEntryResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String employeeName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalTime shiftStart,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalTime shiftEnd,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal hourlyWage,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal workedHours,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal wageCost
) {
}

