package org.thomcgn.backend.shift.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ShiftSettlementResponse(
        Long id,
        LocalDate settlementDate,
        BigDecimal openingCash,
        BigDecimal otherExpenses,
        BigDecimal dailyRevenue,
        BigDecimal totalWages,
        BigDecimal expectedClosingCash,
        List<ShiftWorkerEntryResponse> entries
) {
}

