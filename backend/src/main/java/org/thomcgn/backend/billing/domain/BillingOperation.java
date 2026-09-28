package org.thomcgn.backend.billing.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.thomcgn.backend.common.domain.BaseEntity;

@Getter
@Setter
@Entity
@Table(name = "billing_operations")
public class BillingOperation extends BaseEntity {
    @Column(nullable = false, unique = true, length = 80)
    private String operationKey;
    @Column(nullable = false, length = 32)
    private String operationType;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private TableOrder order;
    @Column(nullable = false, length = 64)
    private String requestFingerprint;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "result_order_id")
    private TableOrder resultOrder;
}
