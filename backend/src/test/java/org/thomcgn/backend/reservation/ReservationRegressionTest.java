package org.thomcgn.backend.reservation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.thomcgn.backend.reservation.api.dto.CreateReservationRequest;
import org.thomcgn.backend.reservation.service.ReservationService;
import org.thomcgn.backend.support.PostgresIntegrationTest;
import java.time.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ReservationRegressionTest extends PostgresIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ReservationService reservations;

    @BeforeEach
    void fixtures() {
        jdbc.execute("truncate table tables restart identity cascade");
        jdbc.update("insert into tables(name,status,active) values('Unconfigured','FREE',true)");
        jdbc.update("update opening_hours set open=true, open_time='17:00', close_time='23:00', second_open_time=null, second_close_time=null");
    }

    @Test
    void unconfiguredCapacityDoesNotSilentlyAcceptAnyGroupSize() {
        assertThatThrownBy(() -> reservations.createReservation(request(LocalDate.now().plusDays(7), "18:00")))
                .isInstanceOf(org.thomcgn.backend.common.exception.ConflictException.class);
    }

    @Test
    void pastReservationsCannotBeCreated() {
        assertThatThrownBy(() -> reservations.createReservation(request(LocalDate.now().minusDays(7), "18:00")))
                .isInstanceOf(org.thomcgn.backend.common.exception.BadRequestException.class);
    }

    @Test
    void noShowJobCatchesMissedPreviousDays() {
        jdbc.update("""
                insert into reservations(guest_name,reservation_date,reservation_time,guest_count,status,qr_code_token,expires_at)
                values('Missed',current_date-2,'18:00',1,'CONFIRMED','missed',current_timestamp - interval '1 day')
                """);
        reservations.markNoShows();
        assertThat(jdbc.queryForObject("select status from reservations where qr_code_token='missed'", String.class)).isEqualTo("NO_SHOW");
    }

    @Test
    void terminalReservationsCannotBeRejectedAgain() {
        jdbc.update("""
                insert into reservations(guest_name,reservation_date,reservation_time,guest_count,status,qr_code_token)
                values('Finished',current_date,'18:00',1,'NO_SHOW','terminal')
                """);
        Long id=jdbc.queryForObject("select id from reservations where qr_code_token='terminal'",Long.class);
        assertThatThrownBy(() -> reservations.cancel(id,"late"))
                .isInstanceOf(org.thomcgn.backend.common.exception.ConflictException.class);
    }

    private CreateReservationRequest request(LocalDate date, String time) {
        return new CreateReservationRequest("Guest",null,null,date,LocalTime.parse(time),20);
    }
}
