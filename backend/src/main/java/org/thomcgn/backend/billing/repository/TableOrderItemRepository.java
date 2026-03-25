package org.thomcgn.backend.billing.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.thomcgn.backend.billing.domain.TableOrderItem;
import org.thomcgn.backend.billing.domain.TableOrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TableOrderItemRepository extends JpaRepository<TableOrderItem, Long> {

    List<TableOrderItem> findByTableOrderId(Long tableOrderId);

    Optional<TableOrderItem> findFirstByTableOrderIdAndDrinkVariantId(Long tableOrderId, Long drinkVariantId);

    List<TableOrderItem> findAllByDrinkVariantIdAndTableOrderStatus(Long drinkVariantId, TableOrderStatus status);

    long deleteByDrinkVariantId(Long drinkVariantId);

    long deleteByDrinkVariantIdIn(List<Long> drinkVariantIds);

    @Query("select coalesce(sum(i.totalPrice), 0) from TableOrderItem i where i.tableOrder.id = :tableOrderId")
    BigDecimal getTotalByTableOrderId(@Param("tableOrderId") Long tableOrderId);

    @Query("""
            select coalesce(sum(i.totalPrice), 0)
            from TableOrderItem i
            where i.tableOrder.status = :status
              and i.tableOrder.paid = true
              and i.tableOrder.closedAt >= :start
              and i.tableOrder.closedAt < :end
            """)
    BigDecimal getRevenueByClosedRange(
            @Param("status") TableOrderStatus status,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("""
            select coalesce(sum(i.deductedVolumeMl), 0)
            from TableOrderItem i
            where i.tableOrder.status = :status
              and i.tableOrder.paid = true
              and i.tableOrder.closedAt >= :start
              and i.tableOrder.closedAt < :end
            """)
    BigDecimal getConsumedVolumeMlByClosedRange(
            @Param("status") TableOrderStatus status,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );
}

