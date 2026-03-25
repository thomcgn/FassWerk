package org.thomcgn.backend.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.thomcgn.backend.inventory.domain.DrinkSalesDaily;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DrinkSalesDailyRepository extends JpaRepository<DrinkSalesDaily, Long> {

    Optional<DrinkSalesDaily> findByDrinkIdAndDrinkVariantIdAndSaleDate(Long drinkId, Long drinkVariantId, LocalDate saleDate);

    List<DrinkSalesDaily> findByDrinkIdAndSaleDateBetween(Long drinkId, LocalDate startDate, LocalDate endDate);

    List<DrinkSalesDaily> findByDrinkVariantIdAndSaleDateBetween(Long drinkVariantId, LocalDate startDate, LocalDate endDate);

    List<DrinkSalesDaily> findBySaleDateBetween(LocalDate startDate, LocalDate endDate);

    @Query("SELECT d FROM DrinkSalesDaily d WHERE d.drinkVariant.id = :variantId AND d.saleDate >= :startDate ORDER BY d.saleDate DESC")
    List<DrinkSalesDaily> findByVariantIdSince(Long variantId, LocalDate startDate);
}

