package org.thomcgn.backend.shift.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record ShiftSettlementRequest(
        @NotNull @DecimalMin("0.00") BigDecimal openingCash,
        @NotNull @DecimalMin("0.00") BigDecimal otherExpenses,
        @Valid List<ShiftWorkerEntryRequest> entries
) {
}

