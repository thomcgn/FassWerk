package org.thomcgn.backend.reservation.application;

import java.util.List;
import java.util.Set;

/** Billing owns physical occupancy; reservations never infer payment from elapsed time. */
public interface ReservationBilling {
    void openForCheckIn(Long reservationId, List<Long> tableIds);
    Set<Long> occupiedTableIds();
    Set<Long> releasedTableIds(Long reservationId);
}
