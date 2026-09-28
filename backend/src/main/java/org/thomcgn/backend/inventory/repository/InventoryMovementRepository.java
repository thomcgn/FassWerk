package org.thomcgn.backend.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.thomcgn.backend.inventory.domain.InventoryMovement;

import java.util.List;
import java.util.Optional;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {

	List<InventoryMovement> findAllByOrderByCreatedAtDesc();

	Optional<InventoryMovement> findByOperationKey(String operationKey);

	long deleteByInventoryItemId(Long inventoryItemId);
}

