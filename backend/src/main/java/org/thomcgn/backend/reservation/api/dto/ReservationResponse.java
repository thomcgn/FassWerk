package org.thomcgn.backend.reservation.api.dto;

import org.thomcgn.backend.reservation.domain.ReservationStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record ReservationResponse(
        Long id,
        String guestName,
        String contactEmail,
        String contactPhone,
        LocalDate reservationDate,
        LocalTime reservationTime,
        Integer guestCount,
        ReservationStatus status,
        LocalDateTime expiresAt,
        LocalDateTime checkedInAt,
        String qrCodeToken,
        String qrScanUrl
) {
}

