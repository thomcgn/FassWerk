package org.thomcgn.backend.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.thomcgn.backend.common.domain.BaseEntity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "reorder_calculations")
public class ReorderCalculation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_item_id", nullable = false)
    private InventoryItem inventoryItem;

    @Column(nullable = false)
    private OffsetDateTime calculationDate;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal currentStockAmount;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal weeklyAverageConsumption;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal recommendedReorderAmount;

    @Column(nullable = false)
    private boolean isBelowThreshold;

    @Column(precision = 6, scale = 2)
    private BigDecimal weeksUntilStockout;
}

