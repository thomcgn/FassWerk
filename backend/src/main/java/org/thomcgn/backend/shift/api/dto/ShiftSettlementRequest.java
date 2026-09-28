package org.thomcgn.backend.shift.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Shift Settlement Request contract")
public record ShiftSettlementRequest(
        @NotNull @DecimalMin("0.00") BigDecimal openingCash,
        @NotNull @DecimalMin("0.00") BigDecimal otherExpenses,
        @Schema(nullable = true)
        @Valid List<ShiftWorkerEntryRequest> entries
) {
}

