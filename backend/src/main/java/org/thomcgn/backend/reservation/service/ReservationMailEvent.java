package org.thomcgn.backend.reservation.service;

import org.thomcgn.backend.reservation.domain.ReservationStatus;
import java.time.LocalDateTime;

/** Immutable mail snapshot: no managed entity or lazy relation escapes the transaction. */
public record ReservationMailEvent(long id, String recipient, String guestName, LocalDateTime start,
                                   LocalDateTime deadline, int guests, ReservationStatus status, String reason) {
    @Override public String toString() { return "ReservationMailEvent[REDACTED]"; }
}
