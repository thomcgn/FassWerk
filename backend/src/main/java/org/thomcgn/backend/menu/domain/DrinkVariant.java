package org.thomcgn.backend.menu.domain;

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

@Getter
@Setter
@Entity
@Table(name = "drink_variants")
public class DrinkVariant extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "drink_id", nullable = false)
    private Drink drink;

    @Column(nullable = false)
    private String displayVolumeName;

    @Column(nullable = false)
    private Integer volumeMl;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "use_volume_standard_price", nullable = false)
    private boolean useVolumeStandardPrice = true;

    @Column
    private String sku;

    @Column(nullable = false)
    private boolean active;
}

