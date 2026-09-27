package org.thomcgn.backend.billing.application;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Immutable read model; no persistence identity or managed entity crosses the boundary. */
public record PaidOrderRevenue(LocalDateTime closedAt, BigDecimal total) {
}
