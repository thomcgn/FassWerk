package org.thomcgn.backend.billing.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.thomcgn.backend.billing.api.dto.AddTableOrderItemRequest;
import org.thomcgn.backend.billing.api.dto.OpenTableOrderRequest;
import org.thomcgn.backend.billing.api.dto.SplitTableOrderPaymentRequest;
import org.thomcgn.backend.billing.api.dto.SplitTableOrderPaymentResponse;
import org.thomcgn.backend.billing.api.dto.TableOrderResponse;
import org.thomcgn.backend.billing.service.TableOrderService;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/table-orders")
@Tag(name = "Table Orders", description = "Tischbon-Flow inkl. Positionen, Split-Payment und Abschluss")
public class TableOrderController {

    private final TableOrderService tableOrderService;

    @PostMapping("/open")
    @Operation(summary = "Neuen offenen Tischbon erstellen")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bon geoeffnet"),
            @ApiResponse(responseCode = "409", description = "Fuer den Tisch existiert bereits ein offener Bon")
    })
    public TableOrderResponse open(@Valid @RequestBody OpenTableOrderRequest request) {
        return tableOrderService.open(request);
    }

    @PostMapping("/{id}/items")
    @Operation(summary = "Position auf offenen Bon buchen")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "200", description = "Position gebucht")
    public TableOrderResponse addItem(@PathVariable Long id, @Valid @RequestBody AddTableOrderItemRequest request) {
        return tableOrderService.addItem(id, request);
    }

    @DeleteMapping("/{id}/items/{itemId}")
    @Operation(summary = "Position vom Bon entfernen")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "200", description = "Position entfernt")
    public TableOrderResponse removeItem(@PathVariable Long id, @PathVariable Long itemId) {
        return tableOrderService.removeItem(id, itemId);
    }

    @PostMapping("/{id}/close")
    @Operation(summary = "Bon abschliessen")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bon abgeschlossen"),
            @ApiResponse(responseCode = "409", description = "Bon kann im aktuellen Zustand nicht abgeschlossen werden")
    })
    public TableOrderResponse close(@PathVariable Long id) {
        return tableOrderService.close(id);
    }

    @PostMapping("/{id}/mark-unpaid")
    @Operation(summary = "Bon als unbezahlt markieren")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "200", description = "Bon auf unbezahlt gesetzt")
    public TableOrderResponse markUnpaid(@PathVariable Long id) {
        return tableOrderService.markUnpaid(id);
    }

    @PostMapping("/{id}/reopen-unpaid")
    @Operation(summary = "Unbezahlten Bon wieder oeffnen")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "200", description = "Bon wieder geoeffnet")
    public TableOrderResponse reopenUnpaid(@PathVariable Long id) {
        return tableOrderService.reopenUnpaid(id);
    }

    @PostMapping("/{id}/split-payment")
    @Operation(summary = "Teilzahlung fuer ausgewaehlte Positionen durchfuehren")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Teilzahlung verarbeitet"),
            @ApiResponse(responseCode = "400", description = "Ungueltige Split-Zuordnung")
    })
    public SplitTableOrderPaymentResponse splitPayment(
            @PathVariable Long id,
            @Valid @RequestBody SplitTableOrderPaymentRequest request
    ) {
        return tableOrderService.splitPayment(id, request);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Bon per ID laden")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bon gefunden"),
            @ApiResponse(responseCode = "404", description = "Bon nicht gefunden")
    })
    public TableOrderResponse getById(@PathVariable Long id) {
        return tableOrderService.getById(id);
    }

    @GetMapping("/open/table/{tableId}")
    @Operation(summary = "Aktuellen offenen Bon pro Tisch laden")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Offener Bon gefunden"),
            @ApiResponse(responseCode = "404", description = "Kein offener Bon vorhanden")
    })
    public TableOrderResponse getOpenByTable(@PathVariable Long tableId) {
        return tableOrderService.getOpenByTable(tableId);
    }

    @GetMapping("/archive")
    @Operation(summary = "Archivierte Bons durchsuchen")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "200", description = "Archivdaten geladen")
    public List<TableOrderResponse> archive(
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) String query,
            @RequestParam(required = false, defaultValue = "ALL") String payment
    ) {
        return tableOrderService.searchArchive(date, query, payment);
    }
}

