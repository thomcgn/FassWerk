package org.thomcgn.backend.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.thomcgn.backend.common.domain.BaseEntity;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "consumption_metadata")
public class ConsumptionMetadata extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_item_id", nullable = false, unique = true)
    private InventoryItem inventoryItem;

    @Column(nullable = false)
    private Integer leadTimeDays = 3;

    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal safetyStockFactor = BigDecimal.valueOf(1.5);

    @Column(nullable = false)
    private Integer weeksLookback = 4;
}

