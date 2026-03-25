package org.thomcgn.backend.reservation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.thomcgn.backend.reservation.domain.Reservation;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationMailService {

    private final JavaMailSender mailSender;

    @Value("${app.reservation.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${app.reservation.mail.from:no-reply@fasswerk.local}")
    private String fromAddress;

    public void sendConfirmedMail(Reservation reservation) {
        sendMail(
                reservation,
                "Reservierung bestätigt",
                "Hallo " + reservation.getGuestName() + ",\n\n"
                        + "deine Reservierung wurde bestätigt.\n"
                        + "Termin: " + reservation.getReservationDate() + " " + reservation.getReservationTime() + "\n"
                        + "Personen: " + reservation.getGuestCount() + "\n\n"
                        + "Bitte beachte: Die bestätigte Reservierung ist nur bis 15 Minuten nach der Buchungszeit gültig.\n"
                        + "Danach kann der Slot verfallen.\n\n"
                        + "Viele Grüße\nFassWerk"
        );
    }

    public void sendRejectedMail(Reservation reservation, String reason) {
        String normalizedReason = reason == null || reason.isBlank()
                ? "Es wurde kein Grund angegeben."
                : reason.trim();

        sendMail(
                reservation,
                "Reservierungsanfrage abgelehnt",
                "Hallo " + reservation.getGuestName() + ",\n\n"
                        + "deine Reservierungsanfrage konnte leider nicht bestätigt werden.\n"
                        + "Termin: " + reservation.getReservationDate() + " " + reservation.getReservationTime() + "\n"
                        + "Personen: " + reservation.getGuestCount() + "\n\n"
                        + "Grund: " + normalizedReason + "\n\n"
                        + "Du kannst gerne eine neue Anfrage senden.\n\n"
                        + "Viele Grüße\nFassWerk"
        );
    }

    private void sendMail(Reservation reservation, String subject, String text) {
        if (!mailEnabled) {
            return;
        }

        if (reservation.getContactEmail() == null || reservation.getContactEmail().isBlank()) {
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(reservation.getContactEmail());
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
        } catch (Exception ex) {
            log.warn("reservation_mail_failed reservationId={} to={} subject={} reason={}",
                    reservation.getId(), reservation.getContactEmail(), subject, ex.getMessage());
        }
    }
}

