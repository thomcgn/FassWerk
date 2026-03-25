package org.thomcgn.backend.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.thomcgn.backend.inventory.domain.InventoryMovement;

import java.util.List;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {

	List<InventoryMovement> findAllByOrderByCreatedAtDesc();

	long deleteByInventoryItemId(Long inventoryItemId);
}

