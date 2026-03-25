package org.thomcgn.backend.billing.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thomcgn.backend.billing.api.dto.AddTableOrderItemRequest;
import org.thomcgn.backend.billing.api.dto.OpenTableOrderRequest;
import org.thomcgn.backend.billing.api.dto.SplitTableOrderItemRequest;
import org.thomcgn.backend.billing.api.dto.SplitTableOrderPaymentRequest;
import org.thomcgn.backend.billing.api.dto.SplitTableOrderPaymentResponse;
import org.thomcgn.backend.billing.api.dto.TableOrderItemResponse;
import org.thomcgn.backend.billing.api.dto.TableOrderResponse;
import org.thomcgn.backend.billing.domain.TableOrder;
import org.thomcgn.backend.billing.domain.TableOrderItem;
import org.thomcgn.backend.billing.domain.TableOrderStatus;
import org.thomcgn.backend.common.exception.BadRequestException;
import org.thomcgn.backend.billing.repository.TableOrderItemRepository;
import org.thomcgn.backend.billing.repository.TableOrderRepository;
import org.thomcgn.backend.common.exception.ConflictException;
import org.thomcgn.backend.common.exception.NotFoundException;
import org.thomcgn.backend.inventory.service.InventoryService;
import org.thomcgn.backend.menu.domain.DrinkVariant;
import org.thomcgn.backend.menu.repository.DrinkVariantRepository;
import org.thomcgn.backend.menu.repository.VolumePriceRepository;
import org.thomcgn.backend.reservation.domain.Reservation;
import org.thomcgn.backend.reservation.repository.ReservationRepository;
import org.thomcgn.backend.table.domain.TableEntity;
import org.thomcgn.backend.table.domain.TableStatus;
import org.thomcgn.backend.table.repository.TableRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TableOrderService {

    private final TableOrderRepository orderRepository;
    private final TableOrderItemRepository itemRepository;
    private final TableRepository tableRepository;
    private final ReservationRepository reservationRepository;
    private final DrinkVariantRepository drinkVariantRepository;
    private final VolumePriceRepository volumePriceRepository;
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
        order.setPaid(false);
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

        if (!variant.isActive() || !variant.getDrink().isActive()) {
            throw new ConflictException("Drink variant is not available");
        }

        BigDecimal quantity = BigDecimal.valueOf(request.quantity());
        BigDecimal deductedVolumeMl = BigDecimal.valueOf(variant.getVolumeMl()).multiply(quantity);
        BigDecimal unitPrice = variant.isUseVolumeStandardPrice()
                ? volumePriceRepository.findByVolumeMl(variant.getVolumeMl())
                        .map(volumePrice -> volumePrice.getPrice())
                        .orElse(variant.getPrice())
                : variant.getPrice();

        inventoryService.assertVariantAvailableForOrder(variant, deductedVolumeMl);

        TableOrderItem savedItem = itemRepository.findFirstByTableOrderIdAndDrinkVariantId(order.getId(), variant.getId())
                .map(existingItem -> {
                    int newQuantity = existingItem.getQuantity() + request.quantity();
                    existingItem.setQuantity(newQuantity);
                    existingItem.setUnitPrice(unitPrice);
                    existingItem.setTotalPrice(unitPrice.multiply(BigDecimal.valueOf(newQuantity)));
                    existingItem.setDeductedVolumeMl(existingItem.getDeductedVolumeMl().add(deductedVolumeMl));
                    return itemRepository.save(existingItem);
                })
                .orElseGet(() -> {
                    TableOrderItem item = new TableOrderItem();
                    item.setTableOrder(order);
                    item.setDrinkVariant(variant);
                    item.setQuantity(request.quantity());
                    item.setUnitPrice(unitPrice);
                    item.setTotalPrice(unitPrice.multiply(quantity));
                    item.setDeductedVolumeMl(deductedVolumeMl);
                    return itemRepository.save(item);
                });

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
        order.setPaid(true);
        order.setClosedAt(LocalDateTime.now());

        TableEntity table = order.getTable();
        table.setStatus(TableStatus.FREE);
        tableRepository.save(table);

        return toResponse(orderRepository.save(order));
    }

    @Transactional
    public TableOrderResponse markUnpaid(Long orderId) {
        TableOrder order = getOpenOrder(orderId);
        order.setStatus(TableOrderStatus.CLOSED);
        order.setPaid(false);
        order.setClosedAt(LocalDateTime.now());

        TableEntity table = order.getTable();
        table.setStatus(TableStatus.FREE);
        tableRepository.save(table);

        return toResponse(orderRepository.save(order));
    }

    @Transactional
    public TableOrderResponse reopenUnpaid(Long orderId) {
        TableOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Table order not found: " + orderId));

        if (order.getStatus() != TableOrderStatus.CLOSED || order.isPaid()) {
            throw new ConflictException("Only unpaid archived table orders can be reopened");
        }

        orderRepository.findFirstByTableIdAndStatus(order.getTable().getId(), TableOrderStatus.OPEN)
                .ifPresent(existing -> {
                    throw new ConflictException("Table already has an open order: " + existing.getId());
                });

        order.setStatus(TableOrderStatus.OPEN);
        order.setPaid(false);
        order.setOpenedAt(LocalDateTime.now());
        order.setClosedAt(null);

        TableEntity table = order.getTable();
        table.setStatus(TableStatus.OCCUPIED);
        tableRepository.save(table);

        return toResponse(orderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public List<TableOrderResponse> searchArchive(LocalDate date, String query, String paymentFilter) {
        LocalDate targetDate = date != null ? date : LocalDate.now();
        LocalDateTime start = targetDate.atStartOfDay();
        LocalDateTime end = targetDate.plusDays(1).atStartOfDay();

        Boolean paid = null;
        if (paymentFilter != null) {
            String normalized = paymentFilter.trim().toUpperCase();
            if ("PAID".equals(normalized)) {
                paid = true;
            } else if ("UNPAID".equals(normalized)) {
                paid = false;
            }
        }

        String queryText = (query == null || query.isBlank()) ? null : query.trim();
        return orderRepository.searchArchive(TableOrderStatus.CLOSED, start, end, queryText, paid)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public SplitTableOrderPaymentResponse splitPayment(Long orderId, SplitTableOrderPaymentRequest request) {
        TableOrder openOrder = getOpenOrder(orderId);
        if (request.items() == null || request.items().isEmpty()) {
            throw new BadRequestException("Split payment requires at least one item");
        }

        Map<Long, Integer> requestedQuantitiesByItem = new LinkedHashMap<>();
        for (SplitTableOrderItemRequest splitItem : request.items()) {
            if (requestedQuantitiesByItem.containsKey(splitItem.itemId())) {
                throw new BadRequestException("Duplicate split item: " + splitItem.itemId());
            }
            requestedQuantitiesByItem.put(splitItem.itemId(), splitItem.quantity());
        }

        List<TableOrderItem> openItems = itemRepository.findByTableOrderId(openOrder.getId());
        Map<Long, TableOrderItem> openItemsById = new LinkedHashMap<>();
        for (TableOrderItem openItem : openItems) {
            openItemsById.put(openItem.getId(), openItem);
        }

        for (Map.Entry<Long, Integer> splitEntry : requestedQuantitiesByItem.entrySet()) {
            TableOrderItem existingItem = openItemsById.get(splitEntry.getKey());
            if (existingItem == null) {
                throw new BadRequestException("Table order item not found on open order: " + splitEntry.getKey());
            }
            if (splitEntry.getValue() > existingItem.getQuantity()) {
                throw new BadRequestException("Split quantity exceeds existing quantity for item: " + splitEntry.getKey());
            }
        }

        LocalDateTime now = LocalDateTime.now();
        TableOrder paidOrder = new TableOrder();
        paidOrder.setTable(openOrder.getTable());
        paidOrder.setReservation(openOrder.getReservation());
        paidOrder.setStatus(TableOrderStatus.CLOSED);
        paidOrder.setPaid(true);
        paidOrder.setOpenedAt(now);
        paidOrder.setClosedAt(now);
        TableOrder savedPaidOrder = orderRepository.save(paidOrder);

        for (Map.Entry<Long, Integer> splitEntry : requestedQuantitiesByItem.entrySet()) {
            TableOrderItem openItem = openItemsById.get(splitEntry.getKey());
            int paidQuantity = splitEntry.getValue();
            BigDecimal paidQuantityDecimal = BigDecimal.valueOf(paidQuantity);
            BigDecimal unitDeductedVolume = openItem.getDeductedVolumeMl().divide(
                    BigDecimal.valueOf(openItem.getQuantity()),
                    4,
                    RoundingMode.HALF_UP
            );

            TableOrderItem paidItem = new TableOrderItem();
            paidItem.setTableOrder(savedPaidOrder);
            paidItem.setDrinkVariant(openItem.getDrinkVariant());
            paidItem.setQuantity(paidQuantity);
            paidItem.setUnitPrice(openItem.getUnitPrice());
            paidItem.setTotalPrice(openItem.getUnitPrice().multiply(paidQuantityDecimal));
            paidItem.setDeductedVolumeMl(unitDeductedVolume.multiply(paidQuantityDecimal).setScale(2, RoundingMode.HALF_UP));
            itemRepository.save(paidItem);

            int remainingQuantity = openItem.getQuantity() - paidQuantity;
            if (remainingQuantity <= 0) {
                itemRepository.delete(openItem);
            } else {
                BigDecimal remainingQuantityDecimal = BigDecimal.valueOf(remainingQuantity);
                openItem.setQuantity(remainingQuantity);
                openItem.setTotalPrice(openItem.getUnitPrice().multiply(remainingQuantityDecimal));
                openItem.setDeductedVolumeMl(unitDeductedVolume.multiply(remainingQuantityDecimal).setScale(2, RoundingMode.HALF_UP));
                itemRepository.save(openItem);
            }
        }

        boolean hasRemainingOpenItems = !itemRepository.findByTableOrderId(openOrder.getId()).isEmpty();
        TableOrder persistedOpenOrder = openOrder;
        if (!hasRemainingOpenItems) {
            openOrder.setStatus(TableOrderStatus.CLOSED);
            openOrder.setPaid(true);
            openOrder.setClosedAt(now);
            tableRepository.findById(openOrder.getTable().getId()).ifPresent(table -> {
                table.setStatus(TableStatus.FREE);
                tableRepository.save(table);
            });
            persistedOpenOrder = orderRepository.save(openOrder);
        }

        return new SplitTableOrderPaymentResponse(
                toResponse(persistedOpenOrder),
                toResponse(savedPaidOrder)
        );
    }

    @Transactional(readOnly = true)
    public TableOrderResponse getById(Long orderId) {
        TableOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Table order not found: " + orderId));
        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public TableOrderResponse getOpenByTable(Long tableId) {
        TableOrder order = orderRepository.findFirstByTableIdAndStatus(tableId, TableOrderStatus.OPEN)
                .orElseThrow(() -> new NotFoundException("Open table order not found for table: " + tableId));
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
                order.isPaid(),
                order.getOpenedAt(),
                order.getClosedAt(),
                total,
                itemResponses
        );
    }
}

