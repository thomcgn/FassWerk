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
import org.thomcgn.backend.menu.domain.Drink;
import org.thomcgn.backend.menu.domain.DrinkVariant;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "inventory_items")
public class InventoryItem extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_drink_id")
    private Drink linkedDrink;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_drink_variant_id")
    private DrinkVariant linkedDrinkVariant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PackageType packageType;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal packagesInStock;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal contentPerPackage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContentUnit contentUnit;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal totalStockAmount;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal reorderThreshold;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal minimumStock;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal recommendedReorderAmount;

    @Column
    private String supplier;

    @Column(nullable = false)
    private boolean active;
}

