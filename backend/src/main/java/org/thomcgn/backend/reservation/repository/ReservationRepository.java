package org.thomcgn.backend.reservation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.thomcgn.backend.reservation.domain.Reservation;
import org.thomcgn.backend.reservation.domain.ReservationStatus;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    Optional<Reservation> findByQrCodeToken(String qrCodeToken);

    @Query("""
            select coalesce(sum(r.guestCount), 0)
            from Reservation r
            where r.reservationDate = :date
              and r.reservationTime = :time
              and r.status in :statuses
            """)
    long getGuestCountForSlot(
            @Param("date") LocalDate date,
            @Param("time") LocalTime time,
            @Param("statuses") Collection<ReservationStatus> statuses
    );

    List<Reservation> findByReservationDateOrderByReservationTimeAsc(LocalDate reservationDate);

    @Query("""
            select r
            from Reservation r
            where r.reservationDate = :date
              and r.status in :statuses
              and r.checkedInAt is null
            """)
    List<Reservation> findUnattendedByDateAndStatuses(
            @Param("date") LocalDate date,
            @Param("statuses") Collection<ReservationStatus> statuses
    );
}

