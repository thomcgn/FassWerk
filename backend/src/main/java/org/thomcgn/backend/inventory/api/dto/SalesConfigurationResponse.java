package org.thomcgn.backend.inventory.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Schema(description = "Sales Configuration Response contract")
public record SalesConfigurationResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer weeksLookback,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal defaultSafetyFactor,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer defaultLeadTimeDays,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String businessTimezone,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalTime businessDayEndsAt,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) LocalDate manualBusinessDate,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate effectiveBusinessDate
) {}

