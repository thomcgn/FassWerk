package org.thomcgn.backend.shift.api.dto;

import java.math.BigDecimal;
import java.time.LocalTime;

public record ShiftWorkerEntryResponse(
        Long id,
        String employeeName,
        LocalTime shiftStart,
        LocalTime shiftEnd,
        BigDecimal hourlyWage,
        BigDecimal workedHours,
        BigDecimal wageCost
) {
}

