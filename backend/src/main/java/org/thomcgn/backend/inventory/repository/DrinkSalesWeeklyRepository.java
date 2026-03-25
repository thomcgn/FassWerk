package org.thomcgn.backend.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.thomcgn.backend.inventory.domain.DrinkSalesWeekly;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DrinkSalesWeeklyRepository extends JpaRepository<DrinkSalesWeekly, Long> {

    Optional<DrinkSalesWeekly> findByDrinkIdAndDrinkVariantIdAndWeekStartDate(Long drinkId, Long drinkVariantId, LocalDate weekStartDate);

    Optional<DrinkSalesWeekly> findByDrinkVariantIdAndWeekStartDateOrderByWeekStartDateDesc(Long drinkVariantId, LocalDate weekStartDate);

    List<DrinkSalesWeekly> findByDrinkVariantIdAndWeekStartDateBetweenOrderByWeekStartDateDesc(Long drinkVariantId, LocalDate startDate, LocalDate endDate);

    @Query("SELECT d FROM DrinkSalesWeekly d WHERE d.drinkVariant.id = :variantId AND d.weekStartDate >= :startDate ORDER BY d.weekStartDate DESC")
    List<DrinkSalesWeekly> findRecentWeeksByVariantId(Long variantId, LocalDate startDate);

    @Query("SELECT AVG(d.averageDailyVolumeMl) FROM DrinkSalesWeekly d WHERE d.drinkVariant.id = :variantId AND d.weekStartDate >= :startDate")
    Double findAverageDailyVolumeByVariantSince(Long variantId, LocalDate startDate);
}

