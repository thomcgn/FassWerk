package org.thomcgn.backend.inventory.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    /**
     * Records a sale of a drink variant.
     * Aggregates quantity and volume sold on a per-day basis.
     */
    @Transactional
    public void recordSale(DrinkVariant drinkVariant, BigDecimal quantity, BigDecimal volumeMl) {
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            log.debug("Ignoring sale with zero or negative quantity for variant: {}", drinkVariant.getId());
            return;
        }

        LocalDate today = salesConfigurationService.getCurrentBusinessDate();
        Drink drink = drinkVariant.getDrink();

        Optional<DrinkSalesDaily> existingDaily = drinkSalesDailyRepository
                .findByDrinkIdAndDrinkVariantIdAndSaleDate(drink.getId(), drinkVariant.getId(), today);

        DrinkSalesDaily dailySale;
        if (existingDaily.isPresent()) {
            dailySale = existingDaily.get();
            dailySale.setQuantitySold(dailySale.getQuantitySold().add(quantity));
            dailySale.setVolumeSoldMl(dailySale.getVolumeSoldMl().add(volumeMl));
        } else {
            dailySale = new DrinkSalesDaily();
            dailySale.setDrink(drink);
            dailySale.setDrinkVariant(drinkVariant);
            dailySale.setSaleDate(today);
            dailySale.setQuantitySold(quantity);
            dailySale.setVolumeSoldMl(volumeMl);
        }

        drinkSalesDailyRepository.save(dailySale);
        log.debug("Recorded sale for variant {} on {}: {} qty, {} ml", drinkVariant.getId(), today, quantity, volumeMl);
    }

    /**
     * Aggregates daily sales data into weekly summaries.
     * Runs daily at 1:00 AM to process previous day's data.
     */
    @Scheduled(cron = "0 15 5 * * *", zone = "Europe/Berlin")
    @Transactional
    public void aggregateDailyToWeekly() {
        LocalDate previousBusinessDate = salesConfigurationService.getCurrentBusinessDate().minusDays(1);
        log.info("Aggregating daily sales to weekly for business date: {}", previousBusinessDate);

        List<DrinkSalesDaily> dailySales = drinkSalesDailyRepository.findBySaleDateBetween(previousBusinessDate, previousBusinessDate);

        for (DrinkSalesDaily daily : dailySales) {
            aggregateToWeekly(daily);
        }

        log.info("Completed aggregation of {} daily sales records", dailySales.size());
    }

    /**
     * Aggregates a single daily sale into the weekly summary.
     */
    @Transactional
    public void aggregateToWeekly(DrinkSalesDaily daily) {
        LocalDate weekStart = getWeekStartDate(daily.getSaleDate());
        Drink drink = daily.getDrink();
        DrinkVariant variant = daily.getDrinkVariant();

        Optional<DrinkSalesWeekly> existingWeekly = drinkSalesWeeklyRepository
                .findByDrinkIdAndDrinkVariantIdAndWeekStartDate(drink.getId(), variant.getId(), weekStart);

        DrinkSalesWeekly weekly;
        if (existingWeekly.isPresent()) {
            weekly = existingWeekly.get();
            weekly.setQuantitySold(weekly.getQuantitySold().add(daily.getQuantitySold()));
            weekly.setVolumeSoldMl(weekly.getVolumeSoldMl().add(daily.getVolumeSoldMl()));
        } else {
            weekly = new DrinkSalesWeekly();
            weekly.setDrink(drink);
            weekly.setDrinkVariant(variant);
            weekly.setWeekStartDate(weekStart);
            weekly.setQuantitySold(daily.getQuantitySold());
            weekly.setVolumeSoldMl(daily.getVolumeSoldMl());
        }

        // Calculate averages (7 days per week)
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

        Double average = drinkSalesWeeklyRepository.findAverageDailyVolumeByVariantSince(variantId, lookbackDate);
        return average != null ? BigDecimal.valueOf(average) : BigDecimal.ZERO;
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

