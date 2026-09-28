package org.thomcgn.backend.inventory.api;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.thomcgn.backend.inventory.api.dto.ConsumptionMetadataRequest;
import org.thomcgn.backend.inventory.api.dto.ConsumptionMetadataResponse;
import org.thomcgn.backend.inventory.api.dto.DrinkSalesDailyResponse;
import org.thomcgn.backend.inventory.api.dto.DrinkSalesWeeklyResponse;
import org.thomcgn.backend.inventory.api.dto.InventoryAdjustmentRequest;
import org.thomcgn.backend.inventory.api.dto.InventoryItemRequest;
import org.thomcgn.backend.inventory.api.dto.InventoryItemResponse;
import org.thomcgn.backend.inventory.api.dto.InventoryMovementResponse;
import org.thomcgn.backend.inventory.api.dto.InventoryPackageDefaultsResponse;
import org.thomcgn.backend.inventory.api.dto.ReorderCalculationResponse;
import org.thomcgn.backend.inventory.api.dto.ReorderSuggestionResponse;
import org.thomcgn.backend.inventory.api.dto.SalesConfigurationRequest;
import org.thomcgn.backend.inventory.api.dto.SalesConfigurationResponse;
import org.thomcgn.backend.inventory.service.InventoryService;
import org.thomcgn.backend.inventory.service.InventoryInsightsApplicationService;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/inventory")
@Tag(name = "Inventory", description = "Lagerverwaltung, Verbrauchstracking, Nachbestellung und Sales-Konfiguration")
@SecurityRequirement(name = "bearerAuth")
public class InventoryController {

    private final InventoryService inventoryService;
    private final InventoryInsightsApplicationService insights;

    @GetMapping
    @Operation(summary = "Lagerartikel auflisten")
    @ApiResponse(responseCode = "200", description = "Lagerliste geladen")
    public List<InventoryItemResponse> list() {
        return inventoryService.listItems();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Lagerartikel anlegen")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Lagerartikel erstellt"),
            @ApiResponse(responseCode = "400", description = "Ungueltige Eingabe")
    })
    public InventoryItemResponse create(@Valid @RequestBody InventoryItemRequest request) {
        return inventoryService.createItem(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Lagerartikel aktualisieren")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lagerartikel aktualisiert"),
            @ApiResponse(responseCode = "404", description = "Lagerartikel nicht gefunden")
    })
    public InventoryItemResponse update(@PathVariable Long id, @Valid @RequestBody InventoryItemRequest request) {
        return inventoryService.updateItem(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Lagerartikel loeschen")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Lagerartikel geloescht"),
            @ApiResponse(responseCode = "404", description = "Lagerartikel nicht gefunden")
    })
    public void delete(@PathVariable Long id) {
        inventoryService.deleteItem(id);
    }

    @PostMapping("/{id}/adjust")
    @Operation(summary = "Lagerbestand manuell korrigieren")
    @ApiResponse(responseCode = "200", description = "Bestand angepasst")
    public InventoryItemResponse adjust(
            @PathVariable Long id,
            @Valid @RequestBody InventoryAdjustmentRequest request,
            Authentication authentication,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        String actor = authentication != null ? authentication.getName() : "system";
        return inventoryService.adjust(id, request, actor, idempotencyKey);
    }

    @GetMapping("/movements")
    @Operation(summary = "Lagerbewegungen abrufen")
    @ApiResponse(responseCode = "200", description = "Bewegungen geladen")
    public List<InventoryMovementResponse> movements() {
        return inventoryService.listMovements();
    }

    @GetMapping("/reorder-suggestions")
    @Operation(summary = "Nachbestellvorschlaege abrufen")
    @ApiResponse(responseCode = "200", description = "Nachbestellvorschlaege geladen")
    public List<ReorderSuggestionResponse> reorderSuggestions() {
        return inventoryService.getReorderSuggestions();
    }

    @GetMapping("/defaults")
    @Operation(summary = "Standardwerte pro Gebindeart abrufen")
    @ApiResponse(responseCode = "200", description = "Standardwerte geladen")
    public List<InventoryPackageDefaultsResponse> defaults() {
        return inventoryService.getPackageDefaults();
    }

    // === Sales Tracking Endpoints ===

    @GetMapping("/sales/daily")
    @Operation(summary = "Taegliche Verkaufsdaten fuer Zeitraum abrufen")
    @ApiResponse(responseCode = "200", description = "Taegliche Verkaufsdaten geladen")
    public List<DrinkSalesDailyResponse> getDailySales(
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate
    ) {
        return insights.getDailySales(startDate, endDate);
    }

    @GetMapping("/sales/weekly/{variantId}")
    @Operation(summary = "Woechentliche Verkaufsaggregation fuer Variante abrufen")
    @ApiResponse(responseCode = "200", description = "Woechentliche Verkaufsdaten geladen")
    public List<DrinkSalesWeeklyResponse> getWeeklySales(
            @PathVariable Long variantId,
            @RequestParam(defaultValue = "4") Integer weeks
    ) {
        return insights.getWeeklySales(variantId, weeks);
    }

    // === Reorder Calculation Endpoints ===

    @GetMapping("/{id}/reorder-calculation")
    @Operation(summary = "Letzte Nachbestellberechnung fuer Lagerartikel abrufen")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Berechnung geladen oder null bei fehlenden Daten"),
            @ApiResponse(responseCode = "404", description = "Lagerartikel nicht gefunden")
    })
    public ReorderCalculationResponse getLatestReorderCalculation(@PathVariable Long id) {
        return insights.getLatestReorderCalculation(id);
    }

    @PostMapping("/{id}/calculate-reorder")
    @Operation(summary = "Nachbestellberechnung fuer Lagerartikel sofort ausfuehren")
    @ApiResponse(responseCode = "200", description = "Berechnung ausgefuehrt")
    public ReorderCalculationResponse calculateReorder(@PathVariable Long id) {
        return insights.calculateReorder(id);
    }

    @GetMapping("/reorder-calculations/below-threshold")
    @Operation(summary = "Artikel unter Schwellwert abrufen")
    @ApiResponse(responseCode = "200", description = "Artikel unter Schwellwert geladen")
    public List<ReorderCalculationResponse> getItemsBelowThreshold() {
        return insights.getItemsBelowThreshold();
    }

    @GetMapping("/{id}/reorder-calculations/history")
    @Operation(summary = "Historie der Nachbestellberechnungen abrufen")
    @ApiResponse(responseCode = "200", description = "Historie geladen")
    public List<ReorderCalculationResponse> getReorderCalculationHistory(
            @PathVariable Long id,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return insights.getReorderCalculationHistory(id, limit);
    }

    // === Consumption Metadata Endpoints ===

    @GetMapping("/{id}/consumption-metadata")
    @Operation(summary = "Verbrauchsmetadaten fuer Lagerartikel abrufen")
    @ApiResponse(responseCode = "200", description = "Metadaten geladen oder leer bei fehlender Konfiguration")
    public ConsumptionMetadataResponse getConsumptionMetadata(@PathVariable Long id) {
        return insights.getConsumptionMetadata(id);
    }

    @PutMapping("/{id}/consumption-metadata")
    @Operation(summary = "Verbrauchsmetadaten fuer Lagerartikel aktualisieren")
    @ApiResponse(responseCode = "200", description = "Verbrauchsmetadaten aktualisiert")
    public ConsumptionMetadataResponse updateConsumptionMetadata(
            @PathVariable Long id,
            @Valid @RequestBody ConsumptionMetadataRequest request
    ) {
        return insights.updateConsumptionMetadata(id, request);
    }

    // === Configuration Endpoints ===

    @GetMapping("/configuration")
    @Operation(summary = "Globale Sales-/Business-Day-Konfiguration laden")
    @ApiResponse(responseCode = "200", description = "Konfiguration geladen")
    public SalesConfigurationResponse getConfiguration() {
        return insights.getConfiguration();
    }

    @PutMapping("/configuration")
    @Operation(summary = "Globale Sales-/Business-Day-Konfiguration speichern")
    @ApiResponse(responseCode = "200", description = "Konfiguration gespeichert")
    public SalesConfigurationResponse updateConfiguration(
            @Valid @RequestBody SalesConfigurationRequest request
    ) {
        return insights.updateConfiguration(request);
    }

    @PostMapping("/configuration/manual-day-close")
    @Operation(summary = "Geschaeftstag manuell abschliessen")
    @ApiResponse(responseCode = "200", description = "Geschaeftstag abgeschlossen")
    public SalesConfigurationResponse closeBusinessDayManually() {
        return insights.closeBusinessDayManually();
    }
}

