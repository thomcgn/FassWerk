package org.thomcgn.backend.billing.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thomcgn.backend.billing.api.dto.AddTableOrderItemRequest;
import org.thomcgn.backend.billing.api.dto.OpenTableOrderRequest;
import org.thomcgn.backend.billing.api.dto.TableOrderItemResponse;
import org.thomcgn.backend.billing.api.dto.TableOrderResponse;
import org.thomcgn.backend.billing.domain.TableOrder;
import org.thomcgn.backend.billing.domain.TableOrderItem;
import org.thomcgn.backend.billing.domain.TableOrderStatus;
import org.thomcgn.backend.billing.repository.TableOrderItemRepository;
import org.thomcgn.backend.billing.repository.TableOrderRepository;
import org.thomcgn.backend.common.exception.ConflictException;
import org.thomcgn.backend.common.exception.NotFoundException;
import org.thomcgn.backend.inventory.service.InventoryService;
import org.thomcgn.backend.menu.domain.DrinkVariant;
import org.thomcgn.backend.menu.repository.DrinkVariantRepository;
import org.thomcgn.backend.reservation.domain.Reservation;
import org.thomcgn.backend.reservation.repository.ReservationRepository;
import org.thomcgn.backend.table.domain.TableEntity;
import org.thomcgn.backend.table.domain.TableStatus;
import org.thomcgn.backend.table.repository.TableRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TableOrderService {

    private final TableOrderRepository orderRepository;
    private final TableOrderItemRepository itemRepository;
    private final TableRepository tableRepository;
    private final ReservationRepository reservationRepository;
    private final DrinkVariantRepository drinkVariantRepository;
    private final InventoryService inventoryService;

    @Transactional
    public TableOrderResponse open(OpenTableOrderRequest request) {
        TableEntity table = tableRepository.findById(request.tableId())
                .orElseThrow(() -> new NotFoundException("Table not found: " + request.tableId()));

        orderRepository.findFirstByTableIdAndStatus(table.getId(), TableOrderStatus.OPEN)
                .ifPresent(existing -> {
                    throw new ConflictException("Table already has an open order: " + existing.getId());
                });

        TableOrder order = new TableOrder();
        order.setTable(table);
        order.setStatus(TableOrderStatus.OPEN);
        order.setOpenedAt(LocalDateTime.now());

        if (request.reservationId() != null) {
            Reservation reservation = reservationRepository.findById(request.reservationId())
                    .orElseThrow(() -> new NotFoundException("Reservation not found: " + request.reservationId()));
            order.setReservation(reservation);
        }

        table.setStatus(TableStatus.OCCUPIED);
        tableRepository.save(table);

        return toResponse(orderRepository.save(order));
    }

    @Transactional
    public TableOrderResponse addItem(Long orderId, AddTableOrderItemRequest request) {
        TableOrder order = getOpenOrder(orderId);
        DrinkVariant variant = drinkVariantRepository.findById(request.drinkVariantId())
                .orElseThrow(() -> new NotFoundException("Drink variant not found: " + request.drinkVariantId()));

        BigDecimal quantity = BigDecimal.valueOf(request.quantity());
        BigDecimal deductedVolumeMl = BigDecimal.valueOf(variant.getVolumeMl()).multiply(quantity);
        BigDecimal totalPrice = variant.getPrice().multiply(quantity);

        TableOrderItem item = new TableOrderItem();
        item.setTableOrder(order);
        item.setDrinkVariant(variant);
        item.setQuantity(request.quantity());
        item.setUnitPrice(variant.getPrice());
        item.setTotalPrice(totalPrice);
        item.setDeductedVolumeMl(deductedVolumeMl);

        TableOrderItem savedItem = itemRepository.save(item);
        inventoryService.deductForOrderItem(variant, deductedVolumeMl, savedItem.getId().toString());

        return toResponse(order);
    }

    @Transactional
    public TableOrderResponse removeItem(Long orderId, Long itemId) {
        TableOrder order = getOpenOrder(orderId);
        TableOrderItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new NotFoundException("Table order item not found: " + itemId));
        if (!item.getTableOrder().getId().equals(order.getId())) {
            throw new ConflictException("Order item does not belong to table order");
        }

        inventoryService.restockForCancelledOrderItem(item.getDrinkVariant(), item.getDeductedVolumeMl(), item.getId().toString());
        itemRepository.delete(item);

        return toResponse(order);
    }

    @Transactional
    public TableOrderResponse close(Long orderId) {
        TableOrder order = getOpenOrder(orderId);
        order.setStatus(TableOrderStatus.CLOSED);
        order.setClosedAt(LocalDateTime.now());

        TableEntity table = order.getTable();
        table.setStatus(TableStatus.READY_FOR_PAYMENT);
        tableRepository.save(table);

        return toResponse(orderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public TableOrderResponse getById(Long orderId) {
        TableOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Table order not found: " + orderId));
        return toResponse(order);
    }

    private TableOrder getOpenOrder(Long orderId) {
        TableOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Table order not found: " + orderId));
        if (order.getStatus() != TableOrderStatus.OPEN) {
            throw new ConflictException("Table order is already closed");
        }
        return order;
    }

    private TableOrderResponse toResponse(TableOrder order) {
        List<TableOrderItemResponse> itemResponses = itemRepository.findByTableOrderId(order.getId()).stream()
                .map(item -> new TableOrderItemResponse(
                        item.getId(),
                        item.getDrinkVariant().getId(),
                        item.getDrinkVariant().getDrink().getName() + " " + item.getDrinkVariant().getDisplayVolumeName(),
                        item.getQuantity(),
                        item.getUnitPrice(),
                        item.getTotalPrice(),
                        item.getDeductedVolumeMl()
                ))
                .toList();

        BigDecimal total = itemRepository.getTotalByTableOrderId(order.getId());

        return new TableOrderResponse(
                order.getId(),
                order.getTable().getId(),
                order.getTable().getName(),
                order.getReservation() != null ? order.getReservation().getId() : null,
                order.getStatus(),
                order.getOpenedAt(),
                order.getClosedAt(),
                total,
                itemResponses
        );
    }
}

