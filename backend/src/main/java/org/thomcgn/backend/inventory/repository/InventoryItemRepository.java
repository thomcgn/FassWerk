package org.thomcgn.backend.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.thomcgn.backend.inventory.domain.InventoryItem;

import java.util.List;
import java.util.Optional;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InventoryItem i where i.id = :id")
    Optional<InventoryItem> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InventoryItem i where i.linkedDrinkVariant.id = :variantId and i.active = true")
    Optional<InventoryItem> findActiveByVariantIdForUpdate(@Param("variantId") Long variantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InventoryItem i where i.linkedDrink.id = :drinkId and i.active = true")
    List<InventoryItem> findActiveByDrinkIdForUpdate(@Param("drinkId") Long drinkId);

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
