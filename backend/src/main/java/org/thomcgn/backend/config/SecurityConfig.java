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

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/auth/login", "/api/auth/refresh", "/api/auth/logout").permitAll()
                        .requestMatchers("/api/auth/sessions/**", "/api/auth/logout-all").hasAnyRole("ADMIN", "STAFF")
                        .requestMatchers(HttpMethod.GET, "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/metrics/**", "/actuator/prometheus").hasRole("ADMIN")

                        .requestMatchers(HttpMethod.POST, "/api/reservations").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/reservations/scan/**", "/api/reservations/*/check-in", "/api/reservations/*/confirm").hasAnyRole("ADMIN", "STAFF")
                        .requestMatchers(HttpMethod.GET, "/api/reservations/**").hasAnyRole("ADMIN", "STAFF")
                        .requestMatchers(HttpMethod.POST, "/api/reservations/*/cancel").hasAnyRole("ADMIN", "STAFF")
                        .requestMatchers(HttpMethod.GET, "/api/reservations/*/qr-code").hasAnyRole("ADMIN", "STAFF")

                        .requestMatchers(HttpMethod.GET, "/api/drink-categories", "/api/drinks", "/api/drink-variants").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/drink-categories/**", "/api/drinks/**", "/api/drink-variants/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/drink-categories/**", "/api/drinks/**", "/api/drink-variants/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/drink-categories/**", "/api/drinks/**", "/api/drink-variants/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/volume-prices/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/volume-prices/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/volume-prices/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/volume-prices/**").hasRole("ADMIN")

                        .requestMatchers(HttpMethod.GET, "/api/tables/**").hasAnyRole("ADMIN", "STAFF")
                        .requestMatchers(HttpMethod.POST, "/api/tables/**").hasAnyRole("ADMIN", "STAFF")
                        .requestMatchers(HttpMethod.PUT, "/api/tables/**").hasAnyRole("ADMIN", "STAFF")

                        .requestMatchers("/api/table-orders/**").hasAnyRole("ADMIN", "STAFF")

                        .requestMatchers(HttpMethod.GET, "/api/inventory", "/api/inventory/defaults", "/api/inventory/movements", "/api/inventory/reorder-suggestions").hasAnyRole("ADMIN", "STAFF")
                        .requestMatchers(HttpMethod.POST, "/api/inventory", "/api/inventory/*/adjust").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/inventory/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/inventory/**").hasRole("ADMIN")

                        .requestMatchers(HttpMethod.GET, "/api/reports/revenue-overview").hasAnyRole("ADMIN", "STAFF")
                        .requestMatchers(HttpMethod.GET, "/api/reports/**").hasRole("ADMIN")

                        .requestMatchers(HttpMethod.GET, "/api/shift-settlements/**").hasAnyRole("ADMIN", "STAFF")
                        .requestMatchers(HttpMethod.PUT, "/api/shift-settlements/**").hasAnyRole("ADMIN", "STAFF")

                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

