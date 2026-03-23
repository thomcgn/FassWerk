package org.thomcgn.backend.reservation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.thomcgn.backend.common.domain.BaseEntity;

@Getter
@Setter
@Entity
@Table(name = "booking_slot_config")
public class BookingSlotConfig extends BaseEntity {

    @Column(nullable = false)
    private Integer slotDurationMinutes;

    @Column(nullable = false)
    private Integer maxReservationDurationMinutes;

    @Column(nullable = false)
    private Integer noShowGracePeriodMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingIntervalMode bookingIntervalMode;

    @Column(nullable = false)
    private boolean active;
}

