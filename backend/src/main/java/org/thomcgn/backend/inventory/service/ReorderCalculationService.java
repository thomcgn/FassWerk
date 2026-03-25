package org.thomcgn.backend.inventory.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thomcgn.backend.inventory.domain.ConsumptionMetadata;
import org.thomcgn.backend.inventory.domain.InventoryItem;
import org.thomcgn.backend.inventory.domain.ReorderCalculation;
import org.thomcgn.backend.inventory.repository.ConsumptionMetadataRepository;
import org.thomcgn.backend.inventory.repository.InventoryItemRepository;
import org.thomcgn.backend.inventory.repository.ReorderCalculationRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReorderCalculationService {

    private final ReorderCalculationRepository reorderCalculationRepository;
    private final ConsumptionMetadataRepository consumptionMetadataRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final DrinkSalesTrackingService drinkSalesTrackingService;
    private final SalesConfigurationService salesConfigurationService;

    /**
     * Calculates the recommended reorder amount for an inventory item based on:
     * - Current stock level
     * - Weekly consumption rate (from sales data)
     * - Reorder threshold
     * - Minimum stock requirement
     * - Lead time and safety stock factor
     */
    @Transactional
    public ReorderCalculation calculateReorderAmount(InventoryItem inventoryItem) {
        if (inventoryItem.getLinkedDrinkVariant() == null) {
            log.warn("Inventory item {} has no linked drink variant, skipping reorder calculation", inventoryItem.getId());
            return null;
        }

        // Get consumption metadata or use defaults
        SalesConfigurationService.SalesConfigurationDto config = salesConfigurationService.getConfiguration();
        Optional<ConsumptionMetadata> metadata = consumptionMetadataRepository.findByInventoryItemId(inventoryItem.getId());
        Integer weeksLookback = metadata.map(ConsumptionMetadata::getWeeksLookback).orElse(config.weeksLookback());
        BigDecimal safetyFactor = metadata.map(ConsumptionMetadata::getSafetyStockFactor).orElse(config.defaultSafetyFactor());
        Integer leadTimeDays = metadata.map(ConsumptionMetadata::getLeadTimeDays).orElse(config.defaultLeadTimeDays());

        // Calculate weekly average consumption
        BigDecimal weeklyAverageConsumption = drinkSalesTrackingService
                .calculateAverageDailyConsumption(inventoryItem.getLinkedDrinkVariant().getId(), weeksLookback);

        // Calculate consumption during lead time
        BigDecimal leadTimeWeeks = BigDecimal.valueOf(leadTimeDays).divide(BigDecimal.valueOf(7), 4, RoundingMode.HALF_UP);
        BigDecimal leadTimeConsumption = weeklyAverageConsumption.multiply(leadTimeWeeks);

        // Calculate safety stock
        BigDecimal safetyStock = weeklyAverageConsumption.multiply(safetyFactor);

        // Calculate recommended reorder amount
        // Formula: (Weekly Avg × Lead Time Weeks) + Safety Stock - Current Stock
        // But ensure it reaches at least the minimum stock and respects reorder threshold
        BigDecimal currentStock = inventoryItem.getTotalStockAmount();
        BigDecimal recommendedAmount = leadTimeConsumption.add(safetyStock).subtract(currentStock);

        // Ensure we don't recommend negative amounts, but respect minimum stock
        if (recommendedAmount.compareTo(BigDecimal.ZERO) < 0) {
            recommendedAmount = BigDecimal.ZERO;
        }

        // Check if below threshold
        boolean isBelowThreshold = currentStock.compareTo(inventoryItem.getReorderThreshold()) < 0;

        // Calculate weeks until stockout at current consumption rate
        BigDecimal weeksUntilStockout = BigDecimal.ZERO;
        if (weeklyAverageConsumption.compareTo(BigDecimal.ZERO) > 0) {
            weeksUntilStockout = currentStock.divide(weeklyAverageConsumption, 2, RoundingMode.HALF_DOWN);
        }

        // Create and save calculation
        ReorderCalculation calculation = new ReorderCalculation();
        calculation.setInventoryItem(inventoryItem);
        calculation.setCalculationDate(OffsetDateTime.now(salesConfigurationService.getBusinessZoneId()));
        calculation.setCurrentStockAmount(currentStock);
        calculation.setWeeklyAverageConsumption(weeklyAverageConsumption);
        calculation.setRecommendedReorderAmount(recommendedAmount);
        calculation.setBelowThreshold(isBelowThreshold);
        calculation.setWeeksUntilStockout(weeksUntilStockout);

        ReorderCalculation saved = reorderCalculationRepository.save(calculation);

        // Update inventory item with calculated value
        inventoryItem.setRecommendedReorderAmount(recommendedAmount);
        inventoryItemRepository.save(inventoryItem);

        log.debug("Calculated reorder for item {}: weekly consumption={}, recommended={}, below_threshold={}",
                inventoryItem.getId(), weeklyAverageConsumption, recommendedAmount, isBelowThreshold);

        return saved;
    }

    /**
     * Recalculates all inventory items (scheduled task).
     * Runs daily at 2:00 AM UTC.
     */
    @Scheduled(cron = "0 30 5 * * *", zone = "Europe/Berlin")
    @Transactional
    public void recalculateAllInventoryItems() {
        log.info("Starting daily reorder calculation for all inventory items");

        List<InventoryItem> inventoryItems = inventoryItemRepository.findByActiveTrue();
        int successCount = 0;
        int failureCount = 0;

        for (InventoryItem item : inventoryItems) {
            try {
                ReorderCalculation calc = calculateReorderAmount(item);
                if (calc != null) {
                    successCount++;
                }
            } catch (Exception e) {
                log.error("Error calculating reorder for inventory item {}: {}", item.getId(), e.getMessage(), e);
                failureCount++;
            }
        }

        log.info("Completed daily reorder calculation: {} successful, {} failed", successCount, failureCount);
    }

    /**
     * Gets the latest reorder calculation for an inventory item.
     */
    public Optional<ReorderCalculation> getLatestCalculation(Long inventoryItemId) {
        return reorderCalculationRepository.findFirstByInventoryItemIdOrderByCalculationDateDesc(inventoryItemId);
    }

    /**
     * Gets all inventory items below their reorder threshold.
     */
    public List<ReorderCalculation> getItemsBelowThreshold() {
        return reorderCalculationRepository.findAllBelowThreshold();
    }

    /**
     * Retrieves calculation history for an inventory item.
     */
    public List<ReorderCalculation> getCalculationHistory(Long inventoryItemId, int limit) {
        List<ReorderCalculation> all = reorderCalculationRepository.findByInventoryItemIdOrderByCalculationDateDesc(inventoryItemId);
        return all.stream().limit(limit).toList();
    }

    /**
     * Creates or updates consumption metadata for an inventory item.
     */
    @Transactional
    public ConsumptionMetadata updateConsumptionMetadata(Long inventoryItemId, Integer leadTimeDays, BigDecimal safetyStockFactor, Integer weeksLookback) {
        Optional<ConsumptionMetadata> existing = consumptionMetadataRepository.findByInventoryItemId(inventoryItemId);

        ConsumptionMetadata metadata;
        if (existing.isPresent()) {
            metadata = existing.get();
        } else {
            metadata = new ConsumptionMetadata();
            InventoryItem item = inventoryItemRepository.findById(inventoryItemId)
                    .orElseThrow(() -> new IllegalArgumentException("Inventory item not found: " + inventoryItemId));
            metadata.setInventoryItem(item);
        }

        if (leadTimeDays != null) {
            metadata.setLeadTimeDays(leadTimeDays);
        }
        if (safetyStockFactor != null) {
            metadata.setSafetyStockFactor(safetyStockFactor);
        }
        if (weeksLookback != null) {
            metadata.setWeeksLookback(weeksLookback);
        }

        return consumptionMetadataRepository.save(metadata);
    }
}

