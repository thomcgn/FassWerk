package org.thomcgn.backend.report.api.dto;

import java.math.BigDecimal;

public record RevenueDayPointResponse(
        String label,
        BigDecimal revenue
) {
}

