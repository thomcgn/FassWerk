package org.thomcgn.backend.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.thomcgn.backend.inventory.domain.ReorderOrder;

import java.time.LocalDate;
import java.util.List;

public interface ReorderOrderRepository extends JpaRepository<ReorderOrder, Long> {

    List<ReorderOrder> findByInventoryItemIdOrderByScheduledDeliveryDateDesc(Long inventoryItemId);

    @Query("""
            select r
            from ReorderOrder r
            where r.status in ('PENDING', 'CONFIRMED', 'SHIPPED')
            order by r.scheduledDeliveryDate asc
            """)
    List<ReorderOrder> findUpcomingDeliveries();

    @Query("""
            select r
            from ReorderOrder r
            where r.scheduledDeliveryDate between :startDate and :endDate
            order by r.scheduledDeliveryDate asc, r.scheduledDeliveryTime asc
            """)
    List<ReorderOrder> findByDateRange(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
            select r
            from ReorderOrder r
            where r.supplier.id = :supplierId
            and r.status in ('PENDING', 'CONFIRMED')
            order by r.scheduledDeliveryDate asc
            """)
    List<ReorderOrder> findPendingBySupplierId(@Param("supplierId") Long supplierId);
}


