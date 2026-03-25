package org.thomcgn.backend.inventory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thomcgn.backend.inventory.domain.InventoryBusinessSettings;
import org.thomcgn.backend.inventory.repository.InventoryBusinessSettingsRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Service für die Verwaltung der Sales Tracking Konfiguration.
 * Lädt und speichert Konfigurationsparameter, die für die Nachbestellberechnung benötigt werden.
 */
@Service
@RequiredArgsConstructor
public class SalesConfigurationService {

    private final InventoryBusinessSettingsRepository inventoryBusinessSettingsRepository;

    @Value("${app.sales.calculation.weeks-lookback:4}")
    private Integer defaultWeeksLookback;

    @Value("${app.sales.calculation.default-safety-factor:1.5}")
    private Double defaultSafetyFactor;

    @Value("${app.sales.calculation.default-lead-time-days:3}")
    private Integer defaultLeadTimeDays;

    @Value("${app.sales.business-timezone:Europe/Berlin}")
    private String defaultBusinessTimezone;

    @Value("${app.sales.business-day-end-time:05:00}")
    private String defaultBusinessDayEndTime;

    /**
     * Gibt die aktuelle Konfiguration zurück.
     */
    public SalesConfigurationDto getConfiguration() {
        InventoryBusinessSettings settings = getOrCreateSettings();
        return new SalesConfigurationDto(
                defaultWeeksLookback,
                BigDecimal.valueOf(defaultSafetyFactor),
                defaultLeadTimeDays,
                settings.getBusinessTimezone(),
                settings.getBusinessDayEndsAt(),
                settings.getManualBusinessDate()
        );
    }

    /**
     * Aktualisiert die Konfiguration.
     * Hinweis: Diese Implementierung liest aus application.yml.
     * Für persistente Änderungen müsste eine zusätzliche Tabelle/Entity verwendet werden.
     */
    @Transactional
    public SalesConfigurationDto updateConfiguration(SalesConfigurationDto config) {
        InventoryBusinessSettings settings = getOrCreateSettings();
        if (config.businessTimezone() != null && !config.businessTimezone().isBlank()) {
            settings.setBusinessTimezone(ZoneId.of(config.businessTimezone()).getId());
        }
        if (config.businessDayEndsAt() != null) {
            settings.setBusinessDayEndsAt(config.businessDayEndsAt());
        }
        settings.setManualBusinessDate(config.manualBusinessDate());
        inventoryBusinessSettingsRepository.save(settings);
        return getConfiguration();
    }

    @Transactional
    public SalesConfigurationDto closeBusinessDayManually() {
        InventoryBusinessSettings settings = getOrCreateSettings();
        LocalDate next = settings.getManualBusinessDate() != null
                ? settings.getManualBusinessDate().plusDays(1)
                : getCurrentBusinessDate().plusDays(1);
        settings.setManualBusinessDate(next);
        inventoryBusinessSettingsRepository.save(settings);
        return getConfiguration();
    }

    public ZoneId getBusinessZoneId() {
        InventoryBusinessSettings settings = getOrCreateSettings();
        return ZoneId.of(settings.getBusinessTimezone());
    }

    public LocalDate getCurrentBusinessDate() {
        InventoryBusinessSettings settings = getOrCreateSettings();
        if (settings.getManualBusinessDate() != null) {
            return settings.getManualBusinessDate();
        }

        ZonedDateTime now = ZonedDateTime.now(ZoneId.of(settings.getBusinessTimezone()));
        LocalDate date = now.toLocalDate();
        if (now.toLocalTime().isBefore(settings.getBusinessDayEndsAt())) {
            return date.minusDays(1);
        }
        return date;
    }

    private InventoryBusinessSettings getOrCreateSettings() {
        return inventoryBusinessSettingsRepository.findTopByOrderByIdAsc().orElseGet(() -> {
            InventoryBusinessSettings settings = new InventoryBusinessSettings();
            settings.setBusinessTimezone(defaultBusinessTimezone);
            settings.setBusinessDayEndsAt(LocalTime.parse(defaultBusinessDayEndTime));
            settings.setManualBusinessDate(null);
            return inventoryBusinessSettingsRepository.save(settings);
        });
    }

    /**
     * DTO für Konfigurationen.
     */
    public record SalesConfigurationDto(
        Integer weeksLookback,
        BigDecimal defaultSafetyFactor,
        Integer defaultLeadTimeDays,
        String businessTimezone,
        LocalTime businessDayEndsAt,
        LocalDate manualBusinessDate
    ) {
    }
}

