package org.thomcgn.backend.shift.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.thomcgn.backend.shift.api.dto.ShiftSettlementRequest;
import org.thomcgn.backend.shift.api.dto.ShiftSettlementResponse;
import org.thomcgn.backend.shift.service.ShiftSettlementService;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/shift-settlements")
@Tag(name = "Shift Settlements", description = "Schicht- und Lohnabrechnungen")
public class ShiftSettlementController {

    private final ShiftSettlementService shiftSettlementService;

    @GetMapping
    @Operation(summary = "Schichtabrechnungen fuer Datumsbereich abrufen")
    public List<ShiftSettlementResponse> listByRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return shiftSettlementService.listByRange(from, to);
    }

    @GetMapping("/{date}")
    @Operation(summary = "Schichtabrechnung fuer ein Datum abrufen")
    public ShiftSettlementResponse getByDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return shiftSettlementService.getByDate(date);
    }

    @PutMapping("/{date}")
    @Operation(summary = "Schichtabrechnung fuer ein Datum speichern")
    public ShiftSettlementResponse saveByDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody ShiftSettlementRequest request
    ) {
        return shiftSettlementService.saveByDate(date, request);
    }
}

