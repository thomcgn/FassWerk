package org.thomcgn.backend.inventory.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.DecimalMin;
import java.time.LocalDate;
import java.time.LocalTime;

@Schema(description = "Sales Configuration Request contract")
public record SalesConfigurationRequest(
    @Schema(nullable = true)
    @Min(1) Integer weeksLookback,
    @Schema(nullable = true)
    @DecimalMin("0.00") BigDecimal defaultSafetyFactor,
    @Schema(nullable = true)
    @Min(0) Integer defaultLeadTimeDays,
    @Schema(nullable = true) String businessTimezone,
    @Schema(nullable = true) LocalTime businessDayEndsAt,
    @Schema(nullable = true) LocalDate manualBusinessDate
) {}

