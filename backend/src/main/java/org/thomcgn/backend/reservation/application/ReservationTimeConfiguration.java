package org.thomcgn.backend.reservation.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class ReservationTimeConfiguration {
    @Bean
    public Clock reservationClock(@Value("${app.reservation.timezone:Europe/Berlin}") String timezone) {
        return Clock.system(ZoneId.of(timezone));
    }
}
