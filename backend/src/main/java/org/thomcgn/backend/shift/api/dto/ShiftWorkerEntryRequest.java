package org.thomcgn.backend.shift.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalTime;

public record ShiftWorkerEntryRequest(
        @NotBlank String employeeName,
        @NotNull LocalTime shiftStart,
        @NotNull LocalTime shiftEnd,
        @NotNull @DecimalMin("0.00") BigDecimal hourlyWage
) {
}

