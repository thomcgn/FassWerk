package org.thomcgn.backend.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.thomcgn.backend.inventory.domain.ConsumptionMetadata;

import java.util.Optional;

@Repository
public interface ConsumptionMetadataRepository extends JpaRepository<ConsumptionMetadata, Long> {

    Optional<ConsumptionMetadata> findByInventoryItemId(Long inventoryItemId);
}

