package org.thomcgn.backend.inventory.api.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.DecimalMin;
import java.time.LocalDate;
import java.time.LocalTime;

public record SalesConfigurationRequest(
    @Min(1) Integer weeksLookback,
    @DecimalMin("0.00") BigDecimal defaultSafetyFactor,
    @Min(0) Integer defaultLeadTimeDays,
    String businessTimezone,
    LocalTime businessDayEndsAt,
    LocalDate manualBusinessDate
) {}

