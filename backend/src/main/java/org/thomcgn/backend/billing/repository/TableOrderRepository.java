package org.thomcgn.backend.billing.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.thomcgn.backend.billing.domain.TableOrder;
import org.thomcgn.backend.billing.domain.TableOrderStatus;

import java.util.Optional;

public interface TableOrderRepository extends JpaRepository<TableOrder, Long> {

    Optional<TableOrder> findFirstByTableIdAndStatus(Long tableId, TableOrderStatus status);
}

