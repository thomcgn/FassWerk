package org.thomcgn.backend.shift.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.thomcgn.backend.shift.domain.ShiftSettlement;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ShiftSettlementRepository extends JpaRepository<ShiftSettlement, Long> {

    Optional<ShiftSettlement> findBySettlementDate(LocalDate settlementDate);

    List<ShiftSettlement> findAllBySettlementDateBetweenOrderBySettlementDateDesc(LocalDate from, LocalDate to);
}

