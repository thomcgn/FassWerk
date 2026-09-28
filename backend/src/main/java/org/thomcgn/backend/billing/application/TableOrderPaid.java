package org.thomcgn.backend.billing.application;

/** In-transaction event: payment, table release and completion commit together. */
public record TableOrderPaid(Long reservationId) {}
