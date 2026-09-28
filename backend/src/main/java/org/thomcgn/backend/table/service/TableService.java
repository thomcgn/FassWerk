package org.thomcgn.backend.table.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thomcgn.backend.common.exception.NotFoundException;
import org.thomcgn.backend.table.api.dto.TableRequest;
import org.thomcgn.backend.table.api.dto.TableResponse;
import org.thomcgn.backend.table.domain.TableEntity;
import org.thomcgn.backend.table.repository.TableRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TableService {

    private final TableRepository tableRepository;
    private final org.thomcgn.backend.reservation.application.ReservationBilling billing;
    private final org.thomcgn.backend.common.persistence.BookingMutationLock bookingLock;
    private final org.thomcgn.backend.reservation.application.ReservationTableUsage reservationUsage;

    @Transactional(readOnly = true)
    public List<TableResponse> list() {
        return tableRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public TableResponse create(TableRequest request) {
        bookingLock.acquire();
        TableEntity table = new TableEntity();
        apply(table, request);
        return toResponse(tableRepository.save(table));
    }

    @Transactional
    public TableResponse update(Long id, TableRequest request) {
        bookingLock.acquire();
        TableEntity table = tableRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Table not found: " + id));
        if (((request.seats() != null && !java.util.Objects.equals(table.getSeats(), request.seats()))
                || !java.util.Objects.equals(table.getArea(), request.area())
                || table.isActive() != request.active() || table.getStatus() != request.status()) && reservationUsage.hasCurrentOrFutureHold(id)) {
            throw new org.thomcgn.backend.common.exception.ConflictException("Table has active reservations; reassign or cancel them before changing capacity, area or availability");
        }
        apply(table, request);
        return toResponse(tableRepository.save(table));
    }

    private void apply(TableEntity table, TableRequest request) {
        table.setName(request.name().trim());
        table.setArea(request.area());
        table.setStatus(request.status());
        table.setActive(request.active());
        if (request.seats() != null) table.setSeats(request.seats());
    }

    private TableResponse toResponse(TableEntity table) {
        return new TableResponse(table.getId(), table.getName(), table.getArea(), billing.occupiedTableIds().contains(table.getId()) ? org.thomcgn.backend.table.domain.TableStatus.OCCUPIED : table.getStatus(), table.isActive(), table.getSeats());
    }
}

