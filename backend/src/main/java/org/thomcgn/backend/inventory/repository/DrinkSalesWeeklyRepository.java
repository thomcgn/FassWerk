package org.thomcgn.backend.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.thomcgn.backend.inventory.domain.DrinkSalesWeekly;

import java.math.BigDecimal;
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

    @Query(value = """
            select avg(average_daily_volume_ml)::numeric
            from drink_sales_weekly
            where drink_variant_id = :variantId and week_start_date >= :startDate
            """, nativeQuery = true)
    BigDecimal findAverageDailyVolumeByVariantSince(
            @Param("variantId") Long variantId,
            @Param("startDate") LocalDate startDate
    );
}

