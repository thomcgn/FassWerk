package org.thomcgn.backend.billing.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Legacy timestamps are decoded by Hibernate with the same binding used to write them. */
public record ClosedRevenue(LocalDate businessDate, LocalDateTime closedAt,
                            BigDecimal revenue, BigDecimal consumedMl) {
    public LocalDate effectiveDate() {
        return businessDate != null ? businessDate : closedAt.toLocalDate();
    }
}
