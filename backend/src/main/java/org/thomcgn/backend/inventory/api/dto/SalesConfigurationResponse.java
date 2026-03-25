package org.thomcgn.backend.inventory.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public record SalesConfigurationResponse(
    Integer weeksLookback,
    BigDecimal defaultSafetyFactor,
    Integer defaultLeadTimeDays,
    String businessTimezone,
    LocalTime businessDayEndsAt,
    LocalDate manualBusinessDate,
    LocalDate effectiveBusinessDate
) {}

