package org.thomcgn.backend.reservation.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import org.thomcgn.backend.common.logging.SafeExceptionDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.thomcgn.backend.reservation.domain.ReservationStatus;

@Slf4j
@Service
public class ReservationMailService {
    private final JavaMailSender mailSender;
    private final Counter sent;
    private final Counter failed;
    private final Counter skipped;

    public ReservationMailService(JavaMailSender mailSender, MeterRegistry registry) {
        this.mailSender = mailSender;
        sent = registry.counter("reservation.mail", "outcome", "sent");
        failed = registry.counter("reservation.mail", "outcome", "failed");
        skipped = registry.counter("reservation.mail", "outcome", "skipped");
    }

    @Value("${app.reservation.mail.enabled:false}") private boolean mailEnabled;
    @Value("${app.reservation.mail.from:no-reply@fasswerk.local}") private String fromAddress;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendDecision(ReservationMailEvent event) {
        if (!mailEnabled || event.recipient() == null || event.recipient().isBlank()) {
            skipped.increment();
            return;
        }
        boolean confirmed = event.status() == ReservationStatus.CONFIRMED;
        String decision = confirmed ? "bestaetigt" : event.status() == ReservationStatus.CANCELLED ? "storniert" : "abgelehnt";
        String text = "Hallo " + event.guestName() + ",\n\ndeine Reservierung wurde " + decision
                + ".\nTermin: " + event.start() + "\nPersonen: " + event.guests()
                + (confirmed ? "\nCheck-in spaetestens bis: " + event.deadline()
                : "\nGrund: " + (event.reason() == null || event.reason().isBlank() ? "Kein Grund angegeben." : event.reason().trim()))
                + "\n\nViele Gruesse\nFassWerk";
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(event.recipient());
            message.setSubject("Reservierung " + decision);
            message.setText(text);
            mailSender.send(message);
            sent.increment();
        } catch (MailException exception) {
            failed.increment();
            log.atWarn().addKeyValue("diagnostic", SafeExceptionDetails.describe(exception))
                    .log("reservation_mail_failed");
        }
    }
}
