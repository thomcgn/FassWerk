package org.thomcgn.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.thomcgn.backend.auth.JwtAuthenticationFilter;
import org.thomcgn.backend.common.api.SecurityErrorResponseWriter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Only explicit Authorization bearer tokens authenticate the backend, never browser cookies.
                .csrf(AbstractHttpConfigurer::disable)
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> SecurityErrorResponseWriter.write(request, response, 401))
                        .accessDeniedHandler((request, response, exception) -> SecurityErrorResponseWriter.write(request, response, 403)))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/auth/login", "/api/auth/refresh", "/api/auth/logout").permitAll()
                        .requestMatchers("/api/auth/sessions/**", "/api/auth/logout-all").hasAnyRole("ADMIN", "BARCHEF", "STAFF")
                        .requestMatchers(HttpMethod.GET, "/v3/api-docs/**", "/v3/api-docs.yaml", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/metrics/**", "/actuator/prometheus").hasRole("ADMIN")

                        .requestMatchers(HttpMethod.GET, "/api/reservations/settings").permitAll()
                        .requestMatchers(HttpMethod.PUT, "/api/reservations/*").hasAnyRole("ADMIN", "BARCHEF", "STAFF")
                        .requestMatchers(HttpMethod.POST, "/api/reservations/*/complete").hasAnyRole("ADMIN", "BARCHEF", "STAFF")
                        .requestMatchers(HttpMethod.POST, "/api/reservations").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/reservations/scan/**", "/api/reservations/*/check-in", "/api/reservations/*/confirm").hasAnyRole("ADMIN", "BARCHEF", "STAFF")
                        .requestMatchers(HttpMethod.GET, "/api/reservations/**").hasAnyRole("ADMIN", "BARCHEF", "STAFF")
                        .requestMatchers(HttpMethod.POST, "/api/reservations/*/cancel").hasAnyRole("ADMIN", "BARCHEF", "STAFF")

                        .requestMatchers(HttpMethod.GET, "/api/drink-categories", "/api/drinks", "/api/drink-variants").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/drink-categories/**", "/api/drinks/**", "/api/drink-variants/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/drink-categories/**", "/api/drinks/**", "/api/drink-variants/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/drink-categories/**", "/api/drinks/**", "/api/drink-variants/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/volume-prices/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/volume-prices/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/volume-prices/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/volume-prices/**").hasRole("ADMIN")


                        .requestMatchers("/api/table-orders/**").hasAnyRole("ADMIN", "BARCHEF", "STAFF")

                        .requestMatchers(HttpMethod.POST, "/api/inventory/*/calculate-reorder", "/api/inventory/configuration/manual-day-close").hasAnyRole("ADMIN", "BARCHEF")
                        .requestMatchers(HttpMethod.GET, "/api/inventory/**").hasAnyRole("ADMIN", "BARCHEF", "STAFF")
                        .requestMatchers(HttpMethod.GET, "/api/reorder/**").hasAnyRole("ADMIN", "BARCHEF", "STAFF")
                        .requestMatchers(HttpMethod.POST, "/api/reorder/**").hasAnyRole("ADMIN", "BARCHEF")
                        .requestMatchers(HttpMethod.PUT, "/api/reorder/**").hasAnyRole("ADMIN", "BARCHEF")
                        .requestMatchers(HttpMethod.POST, "/api/inventory", "/api/inventory/*/adjust").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/inventory/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/inventory/**").hasRole("ADMIN")

                        .requestMatchers(HttpMethod.GET, "/api/reports/revenue-overview").hasAnyRole("ADMIN", "BARCHEF", "STAFF")
                        .requestMatchers(HttpMethod.GET, "/api/reports/**").hasRole("ADMIN")

                        .requestMatchers(HttpMethod.GET, "/api/shift-settlements/**").hasAnyRole("ADMIN", "BARCHEF", "STAFF")
                        .requestMatchers(HttpMethod.PUT, "/api/shift-settlements/**").hasAnyRole("ADMIN", "BARCHEF", "STAFF")

                        .requestMatchers("/api/tables/**").hasAnyRole("ADMIN", "BARCHEF", "STAFF")
                        .anyRequest().denyAll()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration() {
        var registration = new FilterRegistrationBean<>(jwtAuthenticationFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

