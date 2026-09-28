package org.thomcgn.backend.reservation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.thomcgn.backend.qr.QrProperties;
import org.thomcgn.backend.reservation.api.dto.ReservationResponse;
import org.thomcgn.backend.reservation.domain.Reservation;

@Component
@RequiredArgsConstructor
public class ReservationMapper {

    private final QrProperties qrProperties;
    private final java.time.Clock reservationClock;

    public ReservationResponse toResponse(Reservation reservation) {
        String scanUrl = qrProperties.scanBaseUrl().replaceAll("/+$", "") + "/bookings/scan/" + reservation.getQrCodeToken();
        var zone = reservation.getReservationZone() == null ? reservationClock.getZone()
                : java.time.ZoneId.of(reservation.getReservationZone());
        var start = reservation.getStartsAt() != null ? reservation.getStartsAt()
                : reservation.getReservationDate().atTime(reservation.getReservationTime()).atZone(zone).toInstant();
        var deadline = org.thomcgn.backend.reservation.application.ReservationRules.checkInDeadline(start);
        return new ReservationResponse(
                reservation.getId(),
                reservation.getGuestName(),
                reservation.getContactEmail(),
                reservation.getContactPhone(),
                reservation.getReservationDate(),
                reservation.getReservationTime(),
                reservation.getGuestCount(),
                reservation.getStatus(),
                java.time.LocalDateTime.ofInstant(deadline, zone),
                reservation.getCheckedInAt(),
                reservation.getQrCodeToken(),
                scanUrl,
                reservation.getAssignedTables().stream().map(org.thomcgn.backend.table.domain.TableEntity::getId).toList(),
                null,
                reservation.getStartsAt(),
                null,
                reservation.getBusinessDate(),
                reservation.getReservationZone()
        );
    }
}

