package org.thomcgn.backend.reservation.api;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.thomcgn.backend.reservation.api.dto.CreateReservationRequest;
import org.thomcgn.backend.reservation.api.dto.ReservationDecisionRequest;
import org.thomcgn.backend.reservation.api.dto.ReservationResponse;
import org.thomcgn.backend.reservation.api.dto.ReservationScanResponse;
import org.thomcgn.backend.reservation.service.ReservationMapper;
import org.thomcgn.backend.reservation.service.ReservationService;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
@Tag(name = "Reservations", description = "Reservierungsanlage, Entscheidungen, Check-in und QR-Flow")
public class ReservationController {

    private final ReservationService reservationService;
    private final ReservationMapper reservationMapper;

    @PostMapping
    @Operation(summary = "Reservierungsanfrage anlegen", description = "Erstellt eine Reservierung im Status PENDING.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reservierung erstellt"),
            @ApiResponse(responseCode = "400", description = "Ungueltige Eingabe")
    })
    public ReservationResponse create(@Valid @RequestBody CreateReservationRequest request) {
        return reservationMapper.toResponse(reservationService.createReservation(request));
    }

    @GetMapping
    @Operation(summary = "Reservierungen fuer ein Datum listen")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "200", description = "Reservierungen geladen")
    public List<ReservationResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        LocalDate effectiveDate = date != null ? date : LocalDate.now();
        return reservationService.listByDate(effectiveDate).stream().map(reservationMapper::toResponse).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Reservierung per ID abrufen")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reservierung gefunden"),
            @ApiResponse(responseCode = "404", description = "Reservierung nicht gefunden")
    })
    public ReservationResponse getById(@PathVariable Long id) {
        return reservationMapper.toResponse(reservationService.getById(id));
    }

    @PostMapping("/{id}/check-in")
    @Operation(summary = "Reservierung einchecken")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Check-in erfolgt"),
            @ApiResponse(responseCode = "409", description = "Statuswechsel nicht erlaubt")
    })
    public ReservationResponse checkIn(@PathVariable Long id) {
        return reservationMapper.toResponse(reservationService.checkIn(id));
    }

    @PostMapping("/{id}/confirm")
    @Operation(summary = "Reservierungsanfrage bestaetigen")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reservierung bestaetigt"),
            @ApiResponse(responseCode = "404", description = "Reservierung nicht gefunden")
    })
    public ReservationResponse confirm(@PathVariable Long id) {
        return reservationMapper.toResponse(reservationService.confirm(id));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Reservierungsanfrage ablehnen")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reservierung abgelehnt"),
            @ApiResponse(responseCode = "404", description = "Reservierung nicht gefunden")
    })
    public ReservationResponse cancel(@PathVariable Long id, @RequestBody(required = false) ReservationDecisionRequest request) {
        return reservationMapper.toResponse(reservationService.cancel(id, request != null ? request.reason() : null));
    }

    @PostMapping("/scan/{token}")
    @Operation(summary = "QR-Token scannen und Check-in-Faehigkeit pruefen")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Scan erfolgreich"),
            @ApiResponse(responseCode = "404", description = "Token ungueltig oder abgelaufen")
    })
    public ReservationScanResponse scan(@PathVariable String token) {
        return reservationService.scanToken(token);
    }

    @GetMapping(value = "/{id}/qr-code", produces = MediaType.IMAGE_PNG_VALUE)
    @Operation(summary = "QR-Code fuer eine Reservierung als PNG laden")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "QR-Code als PNG geliefert"),
            @ApiResponse(responseCode = "404", description = "Reservierung nicht gefunden")
    })
    public ResponseEntity<byte[]> qrCode(@PathVariable Long id) {
        byte[] payload = reservationService.generateReservationQrCode(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.IMAGE_PNG)
                .body(payload);
    }
}

