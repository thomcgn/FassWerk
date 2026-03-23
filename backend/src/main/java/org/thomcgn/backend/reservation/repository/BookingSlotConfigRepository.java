package org.thomcgn.backend.reservation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.thomcgn.backend.reservation.domain.BookingSlotConfig;

import java.util.Optional;

public interface BookingSlotConfigRepository extends JpaRepository<BookingSlotConfig, Long> {

    Optional<BookingSlotConfig> findFirstByActiveTrueOrderByIdDesc();
}

