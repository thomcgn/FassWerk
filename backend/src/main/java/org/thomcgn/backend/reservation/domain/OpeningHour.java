package org.thomcgn.backend.reservation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.thomcgn.backend.common.domain.BaseEntity;

import java.time.DayOfWeek;
import java.time.LocalTime;

@Getter
@Setter
@Entity
@Table(name = "opening_hours")
public class OpeningHour extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private DayOfWeek weekday;

    @Column(nullable = false)
    private boolean open;

    @Column
    private LocalTime openTime;

    @Column
    private LocalTime closeTime;

    @Column
    private LocalTime secondOpenTime;

    @Column
    private LocalTime secondCloseTime;
}

