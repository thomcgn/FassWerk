package org.thomcgn.backend.reservation.api;

import jakarta.validation.Valid;
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
import org.thomcgn.backend.reservation.api.dto.ReservationResponse;
import org.thomcgn.backend.reservation.api.dto.ReservationScanResponse;
import org.thomcgn.backend.reservation.service.ReservationMapper;
import org.thomcgn.backend.reservation.service.ReservationService;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;
    private final ReservationMapper reservationMapper;

    @PostMapping
    public ReservationResponse create(@Valid @RequestBody CreateReservationRequest request) {
        return reservationMapper.toResponse(reservationService.createReservation(request));
    }

    @GetMapping
    public List<ReservationResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        LocalDate effectiveDate = date != null ? date : LocalDate.now();
        return reservationService.listByDate(effectiveDate).stream().map(reservationMapper::toResponse).toList();
    }

    @GetMapping("/{id}")
    public ReservationResponse getById(@PathVariable Long id) {
        return reservationMapper.toResponse(reservationService.getById(id));
    }

    @PostMapping("/{id}/check-in")
    public ReservationResponse checkIn(@PathVariable Long id) {
        return reservationMapper.toResponse(reservationService.checkIn(id));
    }

    @PostMapping("/{id}/cancel")
    public ReservationResponse cancel(@PathVariable Long id) {
        return reservationMapper.toResponse(reservationService.cancel(id));
    }

    @PostMapping("/scan/{token}")
    public ReservationScanResponse scan(@PathVariable String token) {
        return reservationService.scanToken(token);
    }

    @GetMapping(value = "/{id}/qr-code", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> qrCode(@PathVariable Long id) {
        byte[] payload = reservationService.generateReservationQrCode(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.IMAGE_PNG)
                .body(payload);
    }
}

