package org.thomcgn.backend.billing.application;

import org.thomcgn.backend.common.exception.BadRequestException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Canonical EUR arithmetic at the persistence boundary. */
public final class Money {
    public static final int SCALE = 2;

    private Money() {}

    public static BigDecimal amount(BigDecimal value) {
        if (value == null || value.signum() < 0) {
            throw new BadRequestException("Money amount must be non-negative");
        }
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal multiply(BigDecimal unitPrice, int quantity) {
        if (quantity < 0) throw new BadRequestException("Quantity must be non-negative");
        return amount(unitPrice).multiply(BigDecimal.valueOf(quantity)).setScale(SCALE, RoundingMode.HALF_UP);
    }
}
