package org.thomcgn.backend.billing.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.thomcgn.backend.billing.domain.BillingOperation;

import java.util.Optional;

public interface BillingOperationRepository extends JpaRepository<BillingOperation, Long> {
    Optional<BillingOperation> findByOperationKey(String operationKey);
}
