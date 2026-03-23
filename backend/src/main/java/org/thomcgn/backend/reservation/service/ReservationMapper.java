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

    public ReservationResponse toResponse(Reservation reservation) {
        String scanUrl = qrProperties.scanBaseUrl() + "/api/reservations/scan/" + reservation.getQrCodeToken();
        return new ReservationResponse(
                reservation.getId(),
                reservation.getGuestName(),
                reservation.getContactEmail(),
                reservation.getContactPhone(),
                reservation.getReservationDate(),
                reservation.getReservationTime(),
                reservation.getGuestCount(),
                reservation.getStatus(),
                reservation.getExpiresAt(),
                reservation.getCheckedInAt(),
                reservation.getQrCodeToken(),
                scanUrl
        );
    }
}

