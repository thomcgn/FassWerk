package org.thomcgn.backend.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.thomcgn.backend.inventory.domain.Supplier;

import java.util.List;
import java.util.Optional;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    List<Supplier> findByActiveTrue();

    Optional<Supplier> findByNameIgnoreCase(String name);
}


