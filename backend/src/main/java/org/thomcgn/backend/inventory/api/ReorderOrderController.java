package org.thomcgn.backend.inventory.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.thomcgn.backend.inventory.api.dto.ReorderOrderRequest;
import org.thomcgn.backend.inventory.api.dto.ReorderOrderResponse;
import org.thomcgn.backend.inventory.api.dto.SupplierRequest;
import org.thomcgn.backend.inventory.api.dto.SupplierResponse;
import org.thomcgn.backend.inventory.service.ReorderOrderService;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reorder")
@Tag(name = "Reorder Orders", description = "Nachbestellungen mit Liefertag und Lieferant-Tracking")
public class ReorderOrderController {

    private final ReorderOrderService reorderOrderService;

    // =========== Suppliers ===========

    @PostMapping("/suppliers")
    @Operation(summary = "Neuen Lieferant erstellen")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "200", description = "Lieferant erstellt")
    public SupplierResponse createSupplier(@Valid @RequestBody SupplierRequest request) {
        return reorderOrderService.createSupplier(request);
    }

    @GetMapping("/suppliers")
    @Operation(summary = "Alle Lieferanten abrufen")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "200", description = "Lieferanten geladen")
    public List<SupplierResponse> getAllSuppliers(
            @RequestParam(required = false, defaultValue = "true") boolean onlyActive
    ) {
        return reorderOrderService.getAllSuppliers(onlyActive);
    }

    @PutMapping("/suppliers/{id}")
    @Operation(summary = "Lieferant aktualisieren")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "200", description = "Lieferant aktualisiert")
    public SupplierResponse updateSupplier(
            @PathVariable Long id,
            @Valid @RequestBody SupplierRequest request
    ) {
        return reorderOrderService.updateSupplier(id, request);
    }

    // =========== Reorder Orders ===========

    @PostMapping("/orders")
    @Operation(summary = "Neue Nachbestellung erstellen")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Nachbestellung erstellt"),
            @ApiResponse(responseCode = "404", description = "Artikel oder Lieferant nicht gefunden")
    })
    public ReorderOrderResponse createReorderOrder(@Valid @RequestBody ReorderOrderRequest request) {
        return reorderOrderService.createReorderOrder(request);
    }

    @GetMapping("/orders/upcoming")
    @Operation(summary = "Bevorstehende Lieferungen abrufen")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "200", description = "Lieferungen geladen")
    public List<ReorderOrderResponse> getUpcomingDeliveries() {
        return reorderOrderService.getUpcomingDeliveries();
    }

    @GetMapping("/orders/by-date-range")
    @Operation(summary = "Lieferungen nach Datumsbereich abrufen")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "200", description = "Lieferungen geladen")
    public List<ReorderOrderResponse> getDeliveriesByDateRange(
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate
    ) {
        return reorderOrderService.getDeliveriesByDateRange(startDate, endDate);
    }

    @GetMapping("/orders/inventory/{inventoryItemId}")
    @Operation(summary = "Nachbestellungen fuer Lagerartikel abrufen")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "200", description = "Nachbestellungen geladen")
    public List<ReorderOrderResponse> getReordersByInventoryItem(@PathVariable Long inventoryItemId) {
        return reorderOrderService.getReordersByInventoryItem(inventoryItemId);
    }

    @PutMapping("/orders/{id}/status")
    @Operation(summary = "Status einer Nachbestellung aendern")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "200", description = "Status aktualisiert")
    public ReorderOrderResponse updateReorderStatus(
            @PathVariable Long id,
            @RequestParam String status
    ) {
        return reorderOrderService.updateReorderStatus(id, status);
    }
}


