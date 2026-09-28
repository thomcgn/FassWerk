package org.thomcgn.backend.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.thomcgn.backend.common.domain.BaseEntity;

@Getter
@Setter
@Entity
@Table(name = "suppliers")
public class Supplier extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;

    @Column
    private String contactEmail;

    @Column
    private String contactPhone;

    @Column
    private String website;

    @Column
    private String notes;

    @Column(nullable = false)
    private boolean active;
}

