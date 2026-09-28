package org.thomcgn.backend.billing.application;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Read-only module boundary. Selection of paid, closed orders belongs to Billing. */
public interface BillingRevenueQueries {
    /** Paid revenue grouped by the recorded business date, using [start, end). */
    List<BusinessDayRevenue> businessDays(java.time.LocalDate start, java.time.LocalDate end);
    /** Cash revenue; historical table receipts without payment method retain their existing cash treatment. */
    BigDecimal cashRevenue(java.time.LocalDate date);
    /** Returns revenue for the half-open interval [start, end). */
    BigDecimal revenue(LocalDateTime start, LocalDateTime end);

    /** Returns consumed milliliters for the half-open interval [start, end). */
    BigDecimal consumedVolumeMl(LocalDateTime start, LocalDateTime end);

    /** Preserves the existing chart query's inclusive end, unlike the aggregate queries. */
    List<PaidOrderRevenue> paidOrdersClosedBetweenInclusive(LocalDateTime start, LocalDateTime end);
}
