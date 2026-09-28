package org.thomcgn.backend.billing.application;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BusinessDayRevenue(LocalDate businessDate, BigDecimal revenue, BigDecimal consumedMl) {}
