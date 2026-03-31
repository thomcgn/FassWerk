package org.thomcgn.backend.billing.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.thomcgn.backend.billing.domain.TableOrder;
import org.thomcgn.backend.billing.domain.TableOrderStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TableOrderRepository extends JpaRepository<TableOrder, Long> {

    Optional<TableOrder> findFirstByTableIdAndStatus(Long tableId, TableOrderStatus status);

    List<TableOrder> findAllByStatusAndClosedAtBetween(TableOrderStatus status, LocalDateTime start, LocalDateTime end);

    List<TableOrder> findAllByStatusAndClosedAtGreaterThanEqualAndClosedAtLessThanOrderByClosedAtDesc(
            TableOrderStatus status,
            LocalDateTime start,
            LocalDateTime end
    );

    List<TableOrder> findAllByStatusAndPaidTrueAndClosedAtBetween(TableOrderStatus status, LocalDateTime start, LocalDateTime end);

    List<TableOrder> findAllByStatusAndPaidAndClosedAtGreaterThanEqualAndClosedAtLessThanOrderByClosedAtDesc(
            TableOrderStatus status,
            boolean paid,
            LocalDateTime start,
            LocalDateTime end
    );

    List<TableOrder> findAllByStatusAndPaidFalseOrderByClosedAtDesc(TableOrderStatus status);

    @Query("""
            select o
            from TableOrder o
            where o.status = :status
              and o.closedAt >= :start
              and o.closedAt < :end
              and (:paid is null or o.paid = :paid)
              and (
                :queryText is null
                or lower(cast(o.table.name as string)) like lower(concat('%', :queryText, '%'))
              )
            order by o.closedAt desc
            """)
    List<TableOrder> searchArchive(
            @Param("status") TableOrderStatus status,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("queryText") String queryText,
            @Param("paid") Boolean paid
    );

    @Query("""
            select o
            from TableOrder o
            where o.status = :status
              and o.paid = false
              and (
                :queryText is null
                or lower(cast(o.table.name as string)) like lower(concat('%', :queryText, '%'))
              )
            order by o.closedAt desc
            """)
    List<TableOrder> searchUnpaidArchive(
            @Param("status") TableOrderStatus status,
            @Param("queryText") String queryText
    );
}

