package org.thomcgn.backend.inventory.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thomcgn.backend.common.exception.ConflictException;
import org.thomcgn.backend.inventory.domain.DrinkSalesDaily;
import org.thomcgn.backend.inventory.domain.DrinkSalesWeekly;
import org.thomcgn.backend.inventory.repository.DrinkSalesDailyRepository;
import org.thomcgn.backend.inventory.repository.DrinkSalesWeeklyRepository;
import org.thomcgn.backend.menu.domain.Drink;
import org.thomcgn.backend.menu.domain.DrinkVariant;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DrinkSalesTrackingService {

    private final DrinkSalesDailyRepository drinkSalesDailyRepository;
    private final DrinkSalesWeeklyRepository drinkSalesWeeklyRepository;
    private final SalesConfigurationService salesConfigurationService;
    private final org.thomcgn.backend.common.persistence.BookingMutationLock bookingLock;

    /**
     * Records a sale of a drink variant.
     * Aggregates quantity and volume sold on a per-day basis.
     */
    @Transactional
    public void recordSale(DrinkVariant drinkVariant, BigDecimal quantity, BigDecimal volumeMl) {
        recordSale(drinkVariant, quantity, volumeMl, salesConfigurationService.getCurrentBusinessDate());
    }

    @Transactional
    public void recordSale(DrinkVariant drinkVariant, BigDecimal quantity, BigDecimal volumeMl, LocalDate saleDate) {
        if (quantity.signum() <= 0 || volumeMl.signum() <= 0 || saleDate == null) {
            throw new ConflictException("Sale quantity and volume must be positive");
        }
        adjustDailySale(drinkVariant, quantity, volumeMl, false, saleDate);
    }

    @Transactional
    public void reverseSale(DrinkVariant drinkVariant, BigDecimal quantity, BigDecimal volumeMl) {
        reverseSale(drinkVariant, quantity, volumeMl, salesConfigurationService.getCurrentBusinessDate());
    }

    @Transactional
    public void reverseSale(DrinkVariant drinkVariant, BigDecimal quantity, BigDecimal volumeMl, LocalDate originalDate) {
        if (quantity.signum() <= 0 || volumeMl.signum() <= 0 || originalDate == null) {
            throw new ConflictException("Sale reversal requires positive amounts and the original date");
        }
        adjustDailySale(drinkVariant, quantity.negate(), volumeMl.negate(), true, originalDate);
    }

    private void adjustDailySale(
            DrinkVariant drinkVariant,
            BigDecimal quantityDelta,
            BigDecimal volumeDeltaMl,
            boolean existingRequired, LocalDate today
    ) {
        bookingLock.acquire();
        Drink drink = drinkVariant.getDrink();
        Optional<DrinkSalesDaily> existingDaily = drinkSalesDailyRepository
                .findByDrinkIdAndDrinkVariantIdAndSaleDate(drink.getId(), drinkVariant.getId(), today);

        if (existingRequired && existingDaily.isEmpty()) {
            throw new ConflictException("No daily sale exists for cancellation");
        }

        DrinkSalesDaily dailySale = existingDaily.orElseGet(() -> {
            DrinkSalesDaily created = new DrinkSalesDaily();
            created.setDrink(drink);
            created.setDrinkVariant(drinkVariant);
            created.setSaleDate(today);
            created.setQuantitySold(BigDecimal.ZERO);
            created.setVolumeSoldMl(BigDecimal.ZERO);
            return created;
        });
        BigDecimal quantity = dailySale.getQuantitySold().add(quantityDelta);
        BigDecimal volume = dailySale.getVolumeSoldMl().add(volumeDeltaMl);
        if (quantity.signum() < 0 || volume.signum() < 0) {
            throw new ConflictException("Sale reversal exceeds recorded daily sale");
        }
        dailySale.setQuantitySold(quantity);
        dailySale.setVolumeSoldMl(volume);
        drinkSalesDailyRepository.saveAndFlush(dailySale);
        if (drinkSalesWeeklyRepository.findByDrinkIdAndDrinkVariantIdAndWeekStartDate(
                drink.getId(), drinkVariant.getId(), getWeekStartDate(today)).isPresent()) aggregateToWeekly(dailySale);
        log.debug("Adjusted sale for variant {} on {}: {} qty, {} ml", drinkVariant.getId(), today, quantityDelta, volumeDeltaMl);
    }

    /**
     * Aggregates daily sales data into weekly summaries.
     * Runs daily at 05:15 Europe/Berlin and rebuilds all available weeks, including missed runs.
     */
    @Scheduled(cron = "0 15 5 * * *", zone = "Europe/Berlin")
    @Transactional
    public void aggregateDailyToWeekly() {
        bookingLock.acquire();
        var dailySales = drinkSalesDailyRepository.findAllForAggregation();
        var processed = new java.util.HashSet<String>();
        int unresolved = 0;
        for (DrinkSalesDaily daily : dailySales) {
            if (daily.getDrinkVariant() == null) { unresolved++; continue; }
            String key = daily.getDrinkVariant().getId() + ":" + getWeekStartDate(daily.getSaleDate());
            if (processed.add(key)) aggregateToWeekly(daily);
        }
        if (unresolved > 0) log.warn("sales_legacy_rows_unresolved count={}", unresolved);
        log.info("sales_weeks_rebuilt count={}", processed.size());
    }

    /**
     * Aggregates a single daily sale into the weekly summary.
     */
    @Transactional
    public void aggregateToWeekly(DrinkSalesDaily daily) {
        bookingLock.acquire();
        if (daily.getDrinkVariant() == null) throw new ConflictException("Legacy sale variant requires reconciliation");
        LocalDate weekStart = getWeekStartDate(daily.getSaleDate());
        Drink drink = daily.getDrink();
        DrinkVariant variant = daily.getDrinkVariant();

        Optional<DrinkSalesWeekly> existingWeekly = drinkSalesWeeklyRepository
                .findByDrinkIdAndDrinkVariantIdAndWeekStartDate(drink.getId(), variant.getId(), weekStart);
        List<DrinkSalesDaily> weekSales = drinkSalesDailyRepository
                .findByDrinkVariantIdAndSaleDateBetween(variant.getId(), weekStart, weekStart.plusDays(6));

        DrinkSalesWeekly weekly = existingWeekly.orElseGet(() -> {
            DrinkSalesWeekly created = new DrinkSalesWeekly();
            created.setDrink(drink);
            created.setDrinkVariant(variant);
            created.setWeekStartDate(weekStart);
            return created;
        });
        weekly.setQuantitySold(weekSales.stream()
                .map(DrinkSalesDaily::getQuantitySold).reduce(BigDecimal.ZERO, BigDecimal::add));
        weekly.setVolumeSoldMl(weekSales.stream()
                .map(DrinkSalesDaily::getVolumeSoldMl).reduce(BigDecimal.ZERO, BigDecimal::add));

        // Recompute the whole week, so scheduler retries cannot double-count a day.
        BigDecimal sevenDays = BigDecimal.valueOf(7);
        weekly.setAverageDailyQuantity(weekly.getQuantitySold().divide(sevenDays, 4, java.math.RoundingMode.HALF_UP));
        weekly.setAverageDailyVolumeMl(weekly.getVolumeSoldMl().divide(sevenDays, 4, java.math.RoundingMode.HALF_UP));

        drinkSalesWeeklyRepository.save(weekly);
        log.debug("Aggregated daily sale {} to weekly for week starting {}", daily.getId(), weekStart);
    }

    /**
     * Calculates the average daily consumption for a drink variant over a specific number of weeks.
     *
     * @param variantId The drink variant ID
     * @param weeksLookback Number of weeks to look back
     * @return Average daily volume in ml, or 0 if no data
     */
    public BigDecimal calculateAverageDailyConsumption(Long variantId, Integer weeksLookback) {
        LocalDate lookbackDate = salesConfigurationService.getCurrentBusinessDate().minusWeeks(weeksLookback);

        BigDecimal average = drinkSalesWeeklyRepository.findAverageDailyVolumeByVariantSince(variantId, lookbackDate);
        return average != null ? average : BigDecimal.ZERO;
    }

    /**
     * Gets the week start date (Monday) for a given date.
     */
    private LocalDate getWeekStartDate(LocalDate date) {
        WeekFields weekFields = WeekFields.of(Locale.GERMANY);
        return date.minusDays(date.get(weekFields.dayOfWeek()) - 1);
    }

    /**
     * Retrieves recent weekly sales for a drink variant.
     */
    public List<DrinkSalesWeekly> getRecentWeeklySales(Long variantId, Integer weeks) {
        LocalDate lookbackDate = salesConfigurationService.getCurrentBusinessDate().minusWeeks(weeks);
        return drinkSalesWeeklyRepository.findRecentWeeksByVariantId(variantId, lookbackDate);
    }

    /**
     * Retrieves daily sales for a date range.
     */
    public List<DrinkSalesDaily> getDailySales(LocalDate startDate, LocalDate endDate) {
        return drinkSalesDailyRepository.findBySaleDateBetween(startDate, endDate);
    }
}

