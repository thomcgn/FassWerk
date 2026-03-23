package org.thomcgn.backend.billing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.thomcgn.backend.common.domain.BaseEntity;
import org.thomcgn.backend.menu.domain.DrinkVariant;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "table_order_items")
public class TableOrderItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "table_order_id", nullable = false)
    private TableOrder tableOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "drink_variant_id", nullable = false)
    private DrinkVariant drinkVariant;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal deductedVolumeMl;
}

