package org.thomcgn.backend.inventory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thomcgn.backend.common.exception.BadRequestException;
import org.thomcgn.backend.inventory.api.dto.ConsumptionMetadataRequest;
import org.thomcgn.backend.inventory.api.dto.ConsumptionMetadataResponse;
import org.thomcgn.backend.inventory.api.dto.DrinkSalesDailyResponse;
import org.thomcgn.backend.inventory.api.dto.DrinkSalesWeeklyResponse;
import org.thomcgn.backend.inventory.api.dto.ReorderCalculationResponse;
import org.thomcgn.backend.inventory.api.dto.SalesConfigurationRequest;
import org.thomcgn.backend.inventory.api.dto.SalesConfigurationResponse;
import org.thomcgn.backend.inventory.domain.ConsumptionMetadata;
import org.thomcgn.backend.inventory.domain.ReorderCalculation;

import java.time.LocalDate;
import java.util.List;

/** Inventory use cases own orchestration and DTO mapping within the persistence context. */
@Service
@RequiredArgsConstructor
@Transactional
public class InventoryInsightsApplicationService {
    private final InventoryService inventoryService;
    private final DrinkSalesTrackingService drinkSalesTrackingService;
    private final ReorderCalculationService reorderCalculationService;
    private final SalesConfigurationService salesConfigurationService;

    @Transactional(readOnly = true)
    public List<DrinkSalesDailyResponse> getDailySales(LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) throw new BadRequestException("startDate must not be after endDate");
        return drinkSalesTrackingService.getDailySales(startDate, endDate).stream()
                .map(daily -> new DrinkSalesDailyResponse(
                        daily.getId(),
                        daily.getDrink().getId(),
                        daily.getDrink().getName(),
                        daily.getDrinkVariant() != null ? daily.getDrinkVariant().getId() : null,
                        daily.getDrinkVariant() != null ? daily.getDrinkVariant().getDisplayVolumeName() : null,
                        daily.getSaleDate(),
                        daily.getQuantitySold(),
                        daily.getVolumeSoldMl()
                ))
                .toList();
    }

    // Business settings may be initialized by this query, so the transaction is not read-only.
    public List<DrinkSalesWeeklyResponse> getWeeklySales(Long variantId, Integer weeks) {
        if (weeks == null || weeks < 1) throw new BadRequestException("weeks must be positive");
        return drinkSalesTrackingService.getRecentWeeklySales(variantId, weeks).stream()
                .map(weekly -> new DrinkSalesWeeklyResponse(
                        weekly.getId(),
                        weekly.getDrink().getId(),
                        weekly.getDrink().getName(),
                        weekly.getDrinkVariant() != null ? weekly.getDrinkVariant().getId() : null,
                        weekly.getDrinkVariant() != null ? weekly.getDrinkVariant().getDisplayVolumeName() : null,
                        weekly.getWeekStartDate(),
                        weekly.getQuantitySold(),
                        weekly.getVolumeSoldMl(),
                        weekly.getAverageDailyQuantity(),
                        weekly.getAverageDailyVolumeMl()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public ReorderCalculationResponse getLatestReorderCalculation(Long id) {
        inventoryService.findInventoryItemById(id);
        return reorderCalculationService.getLatestCalculation(id)
                .map(this::toReorderCalculationResponse)
                .orElse(null);
    }

    public ReorderCalculationResponse calculateReorder(Long id) {
        ReorderCalculation calculation = reorderCalculationService.calculateReorderAmount(
                inventoryService.findInventoryItemById(id)
        );
        return calculation == null ? null : toReorderCalculationResponse(calculation);
    }

    @Transactional(readOnly = true)
    public List<ReorderCalculationResponse> getItemsBelowThreshold() {
        return reorderCalculationService.getItemsBelowThreshold().stream()
                .map(this::toReorderCalculationResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReorderCalculationResponse> getReorderCalculationHistory(Long id, int limit) {
        if (limit < 1) throw new BadRequestException("limit must be positive");
        return reorderCalculationService.getCalculationHistory(id, limit).stream()
                .map(this::toReorderCalculationResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ConsumptionMetadataResponse getConsumptionMetadata(Long id) {
        return reorderCalculationService.getConsumptionMetadata(id)
                .map(this::toConsumptionMetadataResponse).orElse(null);
    }

    public ConsumptionMetadataResponse updateConsumptionMetadata(Long id, ConsumptionMetadataRequest request) {
        ConsumptionMetadata metadata = reorderCalculationService.updateConsumptionMetadata(
                id,
                request.leadTimeDays(),
                request.safetyStockFactor(),
                request.weeksLookback()
        );
        return toConsumptionMetadataResponse(metadata);
    }

    public SalesConfigurationResponse getConfiguration() {
        var config = salesConfigurationService.getConfiguration();
        return new SalesConfigurationResponse(
                config.weeksLookback(),
                config.defaultSafetyFactor(),
                config.defaultLeadTimeDays(),
                config.businessTimezone(),
                config.businessDayEndsAt(),
                config.manualBusinessDate(),
                salesConfigurationService.getCurrentBusinessDate()
        );
    }

    public SalesConfigurationResponse updateConfiguration(SalesConfigurationRequest request) {
        var config = salesConfigurationService.updateConfiguration(new SalesConfigurationService.SalesConfigurationDto(
                request.weeksLookback(),
                request.defaultSafetyFactor(),
                request.defaultLeadTimeDays(),
                request.businessTimezone(),
                request.businessDayEndsAt(),
                request.manualBusinessDate()
        ));
        return new SalesConfigurationResponse(
                config.weeksLookback(),
                config.defaultSafetyFactor(),
                config.defaultLeadTimeDays(),
                config.businessTimezone(),
                config.businessDayEndsAt(),
                config.manualBusinessDate(),
                salesConfigurationService.getCurrentBusinessDate()
        );
    }

    public SalesConfigurationResponse closeBusinessDayManually() {
        var config = salesConfigurationService.closeBusinessDayManually();
        return new SalesConfigurationResponse(
                config.weeksLookback(),
                config.defaultSafetyFactor(),
                config.defaultLeadTimeDays(),
                config.businessTimezone(),
                config.businessDayEndsAt(),
                config.manualBusinessDate(),
                salesConfigurationService.getCurrentBusinessDate()
        );
    }

    private ReorderCalculationResponse toReorderCalculationResponse(ReorderCalculation calc) {
        return new ReorderCalculationResponse(
                calc.getId(),
                calc.getInventoryItem().getId(),
                calc.getInventoryItem().getName(),
                calc.getCalculationDate(),
                calc.getCurrentStockAmount(),
                calc.getWeeklyAverageConsumption(),
                calc.getRecommendedReorderAmount(),
                calc.isBelowThreshold(),
                calc.getWeeksUntilStockout()
        );
    }

    private ConsumptionMetadataResponse toConsumptionMetadataResponse(ConsumptionMetadata metadata) {
        return new ConsumptionMetadataResponse(
                metadata.getId(),
                metadata.getInventoryItem().getId(),
                metadata.getLeadTimeDays(),
                metadata.getSafetyStockFactor(),
                metadata.getWeeksLookback()
        );
    }
}
