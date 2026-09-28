package org.thomcgn.backend.shift.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Shift Settlement Response contract")
public record ShiftSettlementResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate settlementDate,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal openingCash,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal otherExpenses,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal dailyRevenue,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal totalWages,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal expectedClosingCash,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ShiftWorkerEntryResponse> entries
) {
}

