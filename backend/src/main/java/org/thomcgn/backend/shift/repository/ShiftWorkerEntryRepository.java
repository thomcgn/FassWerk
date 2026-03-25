package org.thomcgn.backend.shift.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.thomcgn.backend.shift.domain.ShiftWorkerEntry;

public interface ShiftWorkerEntryRepository extends JpaRepository<ShiftWorkerEntry, Long> {
}

