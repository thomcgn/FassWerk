package org.thomcgn.backend.billing.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.thomcgn.backend.billing.domain.TableOrderItem;

import java.math.BigDecimal;
import java.util.List;

public interface TableOrderItemRepository extends JpaRepository<TableOrderItem, Long> {

    List<TableOrderItem> findByTableOrderId(Long tableOrderId);

    @Query("select coalesce(sum(i.totalPrice), 0) from TableOrderItem i where i.tableOrder.id = :tableOrderId")
    BigDecimal getTotalByTableOrderId(@Param("tableOrderId") Long tableOrderId);
}

