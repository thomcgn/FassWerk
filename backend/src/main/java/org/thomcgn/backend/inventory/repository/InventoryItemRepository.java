package org.thomcgn.backend.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.thomcgn.backend.inventory.domain.InventoryItem;

import java.util.List;
import java.util.Optional;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {

    Optional<InventoryItem> findFirstByLinkedDrinkVariantIdAndActiveTrue(Long linkedDrinkVariantId);

    Optional<InventoryItem> findFirstByLinkedDrinkIdAndActiveTrue(Long linkedDrinkId);

    List<InventoryItem> findAllByLinkedDrinkVariantId(Long linkedDrinkVariantId);

    List<InventoryItem> findAllByLinkedDrinkId(Long linkedDrinkId);

    List<InventoryItem> findAllByLinkedDrinkIdAndActiveTrue(Long linkedDrinkId);

    List<InventoryItem> findAllByOrderByNameAsc();

    @Query("""
            select i
            from InventoryItem i
            where i.active = true
              and i.totalStockAmount <= i.reorderThreshold
            order by i.totalStockAmount asc
            """)
    List<InventoryItem> findCriticalForReorder();

    List<InventoryItem> findByActiveTrue();
}
