package org.thomcgn.backend.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.thomcgn.backend.inventory.domain.InventoryBusinessSettings;

import java.util.Optional;

@Repository
public interface InventoryBusinessSettingsRepository extends JpaRepository<InventoryBusinessSettings, Long> {

    Optional<InventoryBusinessSettings> findTopByOrderByIdAsc();
}

