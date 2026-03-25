package org.thomcgn.backend.table.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.thomcgn.backend.table.domain.TableEntity;

public interface TableRepository extends JpaRepository<TableEntity, Long> {

    @Query("select count(t) from TableEntity t where t.active = true")
    long getTotalActiveTables();
}

