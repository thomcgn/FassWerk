package org.thomcgn.backend.reservation.application;

public interface ReservationTableUsage {
    boolean hasCurrentOrFutureHold(Long tableId);
    void assertWalkInAvailable(Long tableId);
}
