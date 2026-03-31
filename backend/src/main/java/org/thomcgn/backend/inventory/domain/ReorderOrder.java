package org.thomcgn.backend.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.thomcgn.backend.common.domain.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "reorder_orders")
public class ReorderOrder extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_item_id", nullable = false)
    private InventoryItem inventoryItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal orderedQuantity;

    @Column(nullable = false)
    private String orderedUnit;

    @Column(nullable = false)
    private LocalDate scheduledDeliveryDate;

    @Column
    private LocalTime scheduledDeliveryTime;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ReorderStatus status;

    @Column
    private String notes;

    @Column(precision = 14, scale = 2)
    private BigDecimal receivedQuantity;

    @Column
    private OffsetDateTime receivedAt;

    @Column
    private String createdBy;

    public enum ReorderStatus {
        PENDING,
        CONFIRMED,
        SHIPPED,
        RECEIVED,
        CANCELLED
    }
}


