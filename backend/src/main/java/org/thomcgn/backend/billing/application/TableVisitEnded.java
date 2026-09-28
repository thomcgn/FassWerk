package org.thomcgn.backend.billing.application;

/** In-transaction event: bill closure, table release and visit completion commit together. */
public record TableVisitEnded(Long reservationId) {}
