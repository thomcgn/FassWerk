package org.thomcgn.backend.menu.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.thomcgn.backend.common.domain.BaseEntity;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "volume_prices")
public class VolumePrice extends BaseEntity {

    @Column(name = "volume_ml", nullable = false, unique = true)
    private Integer volumeMl;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;
}

