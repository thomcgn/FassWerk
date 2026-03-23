package org.thomcgn.backend.reservation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.thomcgn.backend.reservation.domain.OpeningHour;

import java.time.DayOfWeek;
import java.util.Optional;

public interface OpeningHourRepository extends JpaRepository<OpeningHour, Long> {

    Optional<OpeningHour> findByWeekday(DayOfWeek weekday);
}

