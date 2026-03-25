package org.thomcgn.backend.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.thomcgn.backend.inventory.domain.ReorderCalculation;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReorderCalculationRepository extends JpaRepository<ReorderCalculation, Long> {

    Optional<ReorderCalculation> findFirstByInventoryItemIdOrderByCalculationDateDesc(Long inventoryItemId);

    List<ReorderCalculation> findByInventoryItemIdOrderByCalculationDateDesc(Long inventoryItemId);

    @Query("SELECT r FROM ReorderCalculation r WHERE r.isBelowThreshold = true ORDER BY r.calculationDate DESC")
    List<ReorderCalculation> findAllBelowThreshold();

    @Query("SELECT r FROM ReorderCalculation r WHERE r.isBelowThreshold = true AND r.calculationDate >= :since ORDER BY r.calculationDate DESC")
    List<ReorderCalculation> findBelowThresholdSince(OffsetDateTime since);
}

