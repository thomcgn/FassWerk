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
    @Query("""
            select new org.thomcgn.backend.billing.application.ClosedRevenue(
                o.closedBusinessDate, o.closedAt, sum(i.totalPrice), sum(i.deductedVolumeMl))
            from TableOrderItem i join i.tableOrder o
            where o.status = org.thomcgn.backend.billing.domain.TableOrderStatus.CLOSED and o.paid = true
              and ((o.closedBusinessDate >= :startDate and o.closedBusinessDate < :endDate)
                or (o.closedBusinessDate is null and o.closedAt >= :startTime and o.closedAt < :endTime))
            group by o.closedBusinessDate, o.closedAt
            """)
    java.util.List<org.thomcgn.backend.billing.application.ClosedRevenue> businessRevenue(
            java.time.LocalDate startDate, java.time.LocalDate endDate,
            java.time.LocalDateTime startTime, java.time.LocalDateTime endTime);

    @Query("""
            select coalesce(sum(i.totalPrice), 0) from TableOrderItem i join i.tableOrder o
            where o.status = org.thomcgn.backend.billing.domain.TableOrderStatus.CLOSED and o.paid = true
              and (o.paymentMethod is null or o.paymentMethod = org.thomcgn.backend.billing.domain.PaymentMethod.CASH)
              and (o.closedBusinessDate = :date
                or (o.closedBusinessDate is null and o.closedAt >= :start and o.closedAt < :end))
            """)
    BigDecimal cashRevenue(java.time.LocalDate date, LocalDateTime start, LocalDateTime end);

    java.util.Optional<TableOrderItem> findFirstByTableOrderIdAndDrinkVariantIdAndSaleBusinessDateAndUnitPrice(
            Long orderId, Long variantId, java.time.LocalDate saleBusinessDate, BigDecimal unitPrice);

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

