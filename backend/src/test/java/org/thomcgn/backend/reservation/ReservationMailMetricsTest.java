package org.thomcgn.backend.reservation;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thomcgn.backend.reservation.domain.ReservationStatus;
import org.thomcgn.backend.reservation.service.ReservationMailEvent;
import org.thomcgn.backend.reservation.service.ReservationMailService;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReservationMailMetricsTest {
    @Test
    void deliveryOutcomesAreCountedWithoutRecipientLabels() {
        var mail = mock(JavaMailSender.class);
        var registry = new SimpleMeterRegistry();
        var service = new ReservationMailService(mail, registry);
        var event = new ReservationMailEvent(1, "private@example.test", "Private Guest",
                LocalDateTime.of(2035, 1, 1, 18, 0), null, 2, ReservationStatus.REJECTED, "Private reason");
        service.sendDecision(event);
        verifyNoInteractions(mail);
        ReflectionTestUtils.setField(service, "mailEnabled", true);
        service.sendDecision(event);
        doThrow(new MailSendException("private@example.test SMTP password")).when(mail).send(any(SimpleMailMessage.class));
        var logger = (Logger) LoggerFactory.getLogger(ReservationMailService.class);
        var logs = new ListAppender<ILoggingEvent>();
        logs.start();
        logger.addAppender(logs);
        try {
            MDC.put("requestId", "mail-correlation");
            service.sendDecision(event);
            assertThat(logs.list).singleElement().satisfies(entry -> {
                assertThat(entry.getFormattedMessage()).isEqualTo("reservation_mail_failed");
                assertThat(entry.getThrowableProxy()).isNull();
                assertThat(entry.getMDCPropertyMap()).containsEntry("requestId", "mail-correlation");
                assertThat(entry.getKeyValuePairs().toString()).contains("MailSendException")
                        .doesNotContain("private@example.test", "SMTP password", "Private Guest", "Private reason");
            });
        } finally {
            MDC.remove("requestId");
            logger.detachAppender(logs);
            logs.stop();
        }
        for (String outcome : new String[]{"sent", "failed", "skipped"}) {
            assertThat(registry.get("reservation.mail").tag("outcome", outcome).counter().count()).isEqualTo(1);
        }
        assertThat(registry.getMeters()).allSatisfy(meter ->
                assertThat(meter.getId().getTags()).hasSize(1));
    }
}
