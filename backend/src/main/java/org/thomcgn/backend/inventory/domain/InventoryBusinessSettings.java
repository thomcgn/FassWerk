package org.thomcgn.backend.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.thomcgn.backend.common.domain.BaseEntity;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@Entity
@Table(name = "inventory_business_settings")
public class InventoryBusinessSettings extends BaseEntity {

    @Column(nullable = false)
    private String businessTimezone;

    @Column(nullable = false)
    private LocalTime businessDayEndsAt;

    @Column
    private LocalDate manualBusinessDate;
}

