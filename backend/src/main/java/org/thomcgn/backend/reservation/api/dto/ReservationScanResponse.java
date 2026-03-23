package org.thomcgn.backend.reservation.api.dto;

import org.thomcgn.backend.reservation.domain.ReservationStatus;

import java.time.LocalDate;
import java.time.LocalTime;

public record ReservationScanResponse(
        Long reservationId,
        String guestName,
        LocalDate reservationDate,
        LocalTime reservationTime,
        ReservationStatus status,
        boolean checkInAllowed
) {
}

