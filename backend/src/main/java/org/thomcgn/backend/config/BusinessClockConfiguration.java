package org.thomcgn.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;

@Configuration
public class BusinessClockConfiguration {
    @Bean public Clock businessClock() { return Clock.systemUTC(); }
}
