package org.thomcgn.backend.billing.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.thomcgn.backend.billing.domain.TableOrder;
import org.thomcgn.backend.billing.domain.TableOrderStatus;
import org.thomcgn.backend.billing.repository.TableOrderRepository;
import org.thomcgn.backend.common.exception.ConflictException;
import org.thomcgn.backend.common.exception.NotFoundException;
import org.thomcgn.backend.reservation.application.ReservationBilling;
import org.thomcgn.backend.reservation.repository.ReservationRepository;
import org.thomcgn.backend.table.domain.TableStatus;
import org.thomcgn.backend.table.repository.TableRepository;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class ReservationBillingAdapter implements ReservationBilling {
    private final TableOrderRepository orders;
    private final TableRepository tables;
    private final ReservationRepository reservations;

    @Override
    public void openForCheckIn(Long reservationId, List<Long> tableIds) {
        var existing = orders.findAllByReservationId(reservationId);
        var occupied = occupiedTableIds();
        for (Long tableId : tableIds) {
            // Retries must not reopen paid tables or create duplicate bills.
            if (existing.stream().anyMatch(order -> order.getTable().getId().equals(tableId))) continue;
            var table = tables.findById(tableId).orElseThrow(() -> new NotFoundException("Table not found: " + tableId));
            if (!table.isActive() || table.getStatus() != TableStatus.FREE || occupied.contains(tableId))
                throw new ConflictException("Reserved table is occupied: " + tableId);
            var order = new TableOrder();
            order.setReservation(reservations.getReferenceById(reservationId));
            order.setTable(table);
            order.setStatus(TableOrderStatus.OPEN);
            order.setPaid(false);
            order.setOpenedAt(LocalDateTime.now());
            orders.save(order);
            table.setStatus(TableStatus.OCCUPIED);
        }
    }

    @Override
    public Set<Long> occupiedTableIds() {
        return new HashSet<>(orders.findOccupiedTableIds());
    }

    @Override
    public Set<Long> settledTableIds(Long reservationId) {
        var settled = new HashSet<Long>();
        var unpaid = new HashSet<Long>();
        for (var order : orders.findAllByReservationId(reservationId)) {
            if (order.getStatus() == TableOrderStatus.CLOSED && order.isPaid()) settled.add(order.getTable().getId());
            else unpaid.add(order.getTable().getId());
        }
        settled.removeAll(unpaid);
        return settled;
    }
}
