package org.thomcgn.backend.billing.service;

import lombok.RequiredArgsConstructor;
import org.thomcgn.backend.billing.api.dto.DirectSaleRequest;
import org.thomcgn.backend.billing.domain.SaleType;
import org.thomcgn.backend.common.application.IdempotencyKeys;
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
import org.thomcgn.backend.billing.domain.BillingOperation;
import org.thomcgn.backend.billing.application.Money;
import org.thomcgn.backend.common.exception.BadRequestException;
import org.thomcgn.backend.billing.repository.TableOrderItemRepository;
import org.thomcgn.backend.billing.repository.TableOrderRepository;
import org.thomcgn.backend.common.exception.ConflictException;
import org.thomcgn.backend.common.exception.NotFoundException;
import org.thomcgn.backend.inventory.service.InventoryService;
import org.thomcgn.backend.menu.domain.DrinkVariant;
import org.thomcgn.backend.menu.repository.DrinkVariantRepository;
import org.thomcgn.backend.menu.repository.VolumePriceRepository;
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
import java.util.Comparator;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TableOrderService {

    private final TableOrderRepository orderRepository;
    private final org.thomcgn.backend.billing.repository.BillingOperationRepository operationRepository;
    private final TableOrderItemRepository itemRepository;
    private final TableRepository tableRepository;
    private final ReservationRepository reservationRepository;
    private final DrinkVariantRepository drinkVariantRepository;
    private final VolumePriceRepository volumePriceRepository;
    private final InventoryService inventoryService;
    private final org.thomcgn.backend.inventory.service.SalesConfigurationService businessSettings;
    private final org.thomcgn.backend.common.persistence.BookingMutationLock bookingLock;
    private final org.thomcgn.backend.reservation.application.ReservationTableUsage reservationUsage;
    private final org.springframework.context.ApplicationEventPublisher events;

    @Transactional
    public TableOrderResponse directSale(DirectSaleRequest request, String rawKey) {
        bookingLock.acquire();
        String key = IdempotencyKeys.optional(rawKey);
        if (key == null) throw new BadRequestException("Direct sale requires Idempotency-Key");
        if (request.paymentMethod() == null || request.items() == null || request.items().isEmpty()
                || request.items().size() > 100) throw new BadRequestException("Invalid direct sale");
        java.util.Set<Long> variants = new java.util.HashSet<>();
        for (var item : request.items()) {
            if (item == null || item.drinkVariantId() == null || item.quantity() == null
                    || item.quantity() < 1 || item.quantity() > 1000 || item.expectedUnitPrice() == null
                    || item.expectedUnitPrice().signum() < 0 || !variants.add(item.drinkVariantId()))
                throw new BadRequestException("Invalid or duplicate direct sale item");
        }
        String canonical = request.paymentMethod().name() + ":" + request.items().stream()
                .sorted(Comparator.comparing(DirectSaleRequest.Item::drinkVariantId))
                .map(i -> i.drinkVariantId() + ":" + i.quantity() + ":" + i.expectedUnitPrice().stripTrailingZeros().toPlainString())
                .collect(java.util.stream.Collectors.joining(","));
        String fingerprint = IdempotencyKeys.fingerprint(canonical);
        var previous = operationRepository.findByOperationKey(key);
        if (previous.isPresent()) {
            var operation = previous.get();
            if (!operation.getOperationType().equals("DIRECT_SALE")
                    || !operation.getRequestFingerprint().equals(fingerprint))
                throw new ConflictException("Idempotency-Key was already used for a different command");
            return toResponse(operation.getOrder());
        }
        TableOrder order = new TableOrder();
        order.setSaleType(SaleType.DIRECT);
        order.setPaymentMethod(request.paymentMethod());
        order.setStatus(TableOrderStatus.OPEN);
        order.setPaid(false);
        order.setOpenedAt(businessSettings.currentVenueTime());
        orderRepository.saveAndFlush(order);
        for (var item : request.items()) {
            var result = addItem(order.getId(), new AddTableOrderItemRequest(item.drinkVariantId(), item.quantity()));
            var booked = result.items().stream().filter(i -> i.drinkVariantId().equals(item.drinkVariantId())).findFirst().orElseThrow();
            if (booked.unitPrice().compareTo(item.expectedUnitPrice()) != 0)
                throw new ConflictException("Price changed; refresh the catalog and confirm the new price");
        }
        close(order.getId());
        recordOperation(key, "DIRECT_SALE", order, fingerprint, null);
        return toResponse(order);
    }

    @Transactional
    public TableOrderResponse open(OpenTableOrderRequest request) {
        bookingLock.acquire();
        TableEntity table = tableRepository.findById(request.tableId())
                .orElseThrow(() -> new NotFoundException("Table not found: " + request.tableId()));

        if (!table.isActive()) throw new ConflictException("Table is inactive");
        if (orderRepository.hasOpenOrders(table.getId()))
            throw new ConflictException("Table already has an open bill");
        if (request.reservationId() != null)
            throw new ConflictException("Use reservation check-in to open its table bills");
        reservationUsage.assertWalkInAvailable(table.getId());

        TableOrder order = new TableOrder();
        order.setTable(table);
        order.setStatus(TableOrderStatus.OPEN);
        order.setPaid(false);
        order.setOpenedAt(businessSettings.currentVenueTime());

        table.setStatus(TableStatus.OCCUPIED);
        tableRepository.save(table);

        return toResponse(orderRepository.save(order));
    }

    @Transactional
    public TableOrderResponse addItem(Long orderId, AddTableOrderItemRequest request) {
        return addItem(orderId, request, null);
    }

    @Transactional
    public TableOrderResponse addItem(Long orderId, AddTableOrderItemRequest request, String idempotencyKey) {
        bookingLock.acquire();
        String fingerprint = org.thomcgn.backend.common.application.IdempotencyKeys.fingerprint(
                request.drinkVariantId() + ":" + request.quantity());
        var replay = replay(idempotencyKey, "ADD_ITEM", orderId, fingerprint);
        if (replay.isPresent()) return toResponse(replay.get().getOrder());
        TableOrder order = getOpenOrder(orderId);
        DrinkVariant variant = drinkVariantRepository.findById(request.drinkVariantId())
                .orElseThrow(() -> new NotFoundException("Drink variant not found: " + request.drinkVariantId()));

        if (!variant.isActive() || !variant.getDrink().isActive()) {
            throw new ConflictException("Drink variant is not available");
        }

        BigDecimal quantity = BigDecimal.valueOf(request.quantity());
        BigDecimal deductedVolumeMl = BigDecimal.valueOf(variant.getVolumeMl()).multiply(quantity);
        BigDecimal unitPrice = Money.amount(variant.isUseVolumeStandardPrice()
                ? volumePriceRepository.findByVolumeMl(variant.getVolumeMl())
                        .map(volumePrice -> volumePrice.getPrice())
                        .orElse(variant.getPrice())
                : variant.getPrice());

        LocalDate saleDate = businessSettings.getCurrentBusinessDate();
        TableOrderItem savedItem = itemRepository.findFirstByTableOrderIdAndDrinkVariantIdAndSaleBusinessDateAndUnitPrice(order.getId(), variant.getId(), saleDate, unitPrice)
                .map(existingItem -> {
                    int newQuantity = existingItem.getQuantity() + request.quantity();
                    existingItem.setQuantity(newQuantity);
                    existingItem.setUnitPrice(unitPrice);
                    existingItem.setTotalPrice(Money.multiply(unitPrice, newQuantity));
                    existingItem.setDeductedVolumeMl(existingItem.getDeductedVolumeMl().add(deductedVolumeMl));
                    return itemRepository.save(existingItem);
                })
                .orElseGet(() -> {
                    TableOrderItem item = new TableOrderItem();
                    item.setTableOrder(order);
                    item.setDrinkVariant(variant);
                    item.setQuantity(request.quantity());
                    item.setSaleBusinessDate(saleDate);
                    item.setUnitPrice(unitPrice);
                    item.setTotalPrice(Money.multiply(unitPrice, request.quantity()));
                    item.setDeductedVolumeMl(deductedVolumeMl);
                    return itemRepository.save(item);
                });

        inventoryService.deductForOrderItem(variant, deductedVolumeMl, savedItem.getId().toString(), saleDate);
        recordOperation(idempotencyKey, "ADD_ITEM", order, fingerprint, null);
        return toResponse(order);
    }

    @Transactional
    public TableOrderResponse removeItem(Long orderId, Long itemId) {
        return removeItem(orderId, itemId, null);
    }

    @Transactional
    public TableOrderResponse removeItem(Long orderId, Long itemId, String idempotencyKey) {
        bookingLock.acquire();
        String fingerprint = org.thomcgn.backend.common.application.IdempotencyKeys.fingerprint(itemId.toString());
        var replay = replay(idempotencyKey, "REMOVE_ITEM", orderId, fingerprint);
        if (replay.isPresent()) return toResponse(replay.get().getOrder());
        TableOrder order = getOpenOrder(orderId);
        TableOrderItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new NotFoundException("Table order item not found: " + itemId));
        if (!item.getTableOrder().getId().equals(order.getId())) {
            throw new ConflictException("Order item does not belong to table order");
        }

        BigDecimal unitDeductedVolume = item.getDeductedVolumeMl().divide(
                BigDecimal.valueOf(item.getQuantity()),
                4,
                RoundingMode.HALF_UP
        );
        if (item.getSaleBusinessDate() == null) throw new ConflictException("Legacy sale date requires reconciliation before cancellation");
        inventoryService.restockForCancelledOrderItem(item.getDrinkVariant(), unitDeductedVolume,
                item.getId().toString(), item.getSaleBusinessDate());

        if (item.getQuantity() <= 1) {
            itemRepository.delete(item);
        } else {
            int remainingQuantity = item.getQuantity() - 1;
            item.setQuantity(remainingQuantity);
            item.setTotalPrice(Money.multiply(item.getUnitPrice(), remainingQuantity));
            item.setDeductedVolumeMl(unitDeductedVolume.multiply(BigDecimal.valueOf(remainingQuantity)).setScale(4, RoundingMode.HALF_UP));
            itemRepository.save(item);
        }

        recordOperation(idempotencyKey, "REMOVE_ITEM", order, fingerprint, null);
        return toResponse(order);
    }

    @Transactional
    public TableOrderResponse close(Long orderId) {
        return close(orderId, null);
    }

    @Transactional
    public TableOrderResponse close(Long orderId, String idempotencyKey) {
        bookingLock.acquire();
        var existingOperation = replay(idempotencyKey, "CLOSE", orderId, "state");
        if (existingOperation.isPresent()) return toResponse(existingOperation.get().getOrder());
        TableOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Table order not found: " + orderId));
        if (order.getStatus() == TableOrderStatus.CLOSED && order.isPaid()) return stateOperationResponse(idempotencyKey, "CLOSE", order);
        if (order.getStatus() != TableOrderStatus.OPEN) throw new ConflictException("Unpaid archived bill must be reopened before payment");
        order.setStatus(TableOrderStatus.CLOSED);
        order.setPaid(true);
        order.setClosedAt(businessSettings.currentVenueTime());
        order.setClosedBusinessDate(businessSettings.getCurrentBusinessDate());

        orderRepository.saveAndFlush(order);
        releaseAfterClose(order);
        return stateOperationResponse(idempotencyKey, "CLOSE", order);
    }

    @Transactional
    public TableOrderResponse markUnpaid(Long orderId) {
        return markUnpaid(orderId, null);
    }

    @Transactional
    public TableOrderResponse markUnpaid(Long orderId, String idempotencyKey) {
        bookingLock.acquire();
        var existingOperation = replay(idempotencyKey, "MARK_UNPAID", orderId, "state");
        if (existingOperation.isPresent()) return toResponse(existingOperation.get().getOrder());
        TableOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Table order not found: " + orderId));
        if (order.getStatus() == TableOrderStatus.CLOSED && !order.isPaid()) return stateOperationResponse(idempotencyKey, "MARK_UNPAID", order);
        if (order.getStatus() != TableOrderStatus.OPEN) throw new ConflictException("Paid bill cannot be archived as unpaid");
        order.setStatus(TableOrderStatus.CLOSED);
        order.setPaid(false);
        order.setClosedAt(businessSettings.currentVenueTime());
        order.setClosedBusinessDate(businessSettings.getCurrentBusinessDate());

        orderRepository.saveAndFlush(order);
        releaseAfterClose(order);
        return stateOperationResponse(idempotencyKey, "MARK_UNPAID", order);
    }

    @Transactional
    public TableOrderResponse reopenUnpaid(Long orderId) {
        return reopenUnpaid(orderId, null);
    }

    @Transactional
    public TableOrderResponse reopenUnpaid(Long orderId, String idempotencyKey) {
        bookingLock.acquire();
        var existingOperation = replay(idempotencyKey, "REOPEN_UNPAID", orderId, "state");
        if (existingOperation.isPresent()) return toResponse(existingOperation.get().getOrder());
        TableOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Table order not found: " + orderId));

        if (order.getStatus() == TableOrderStatus.OPEN) return stateOperationResponse(idempotencyKey, "REOPEN_UNPAID", order);
        if (order.getStatus() != TableOrderStatus.CLOSED || order.isPaid()) {
            throw new ConflictException("Only unpaid archived table orders can be reopened");
        }

        orderRepository.findFirstByTableIdAndStatus(order.getTable().getId(), TableOrderStatus.OPEN)
                .ifPresent(existing -> {
                    throw new ConflictException("Table already has an open order: " + existing.getId());
                });

        if (!order.getTable().isActive()) throw new ConflictException("Table is inactive");
        reservationUsage.assertWalkInAvailable(order.getTable().getId());

        order.setStatus(TableOrderStatus.OPEN);
        order.setPaid(false);
        order.setOpenedAt(businessSettings.currentVenueTime());
        order.setClosedAt(null);
        order.setClosedBusinessDate(null);

        TableEntity table = order.getTable();
        table.setStatus(TableStatus.OCCUPIED);
        tableRepository.save(table);

        return stateOperationResponse(idempotencyKey, "REOPEN_UNPAID", orderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public List<TableOrderResponse> searchArchive(LocalDate date, String query, String paymentFilter) {
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

        if (Boolean.FALSE.equals(paid)) {
            if (queryText == null) {
                return orderRepository.findAllByStatusAndPaidFalseOrderByClosedAtDesc(TableOrderStatus.CLOSED)
                        .stream()
                        .map(this::toResponse)
                        .toList();
            }
            return orderRepository.searchUnpaidArchive(TableOrderStatus.CLOSED, queryText)
                    .stream()
                    .map(this::toResponse)
                    .toList();
        }

        LocalDate targetDate = date != null ? date : businessSettings.getCurrentBusinessDate();
        LocalDateTime start = targetDate.atStartOfDay();
        LocalDateTime end = targetDate.plusDays(1).atStartOfDay();
        return orderRepository.searchArchive(TableOrderStatus.CLOSED, targetDate, start, end, queryText, paid)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public SplitTableOrderPaymentResponse splitPayment(Long orderId, SplitTableOrderPaymentRequest request) {
        return splitPayment(orderId, request, null);
    }

    @Transactional
    public SplitTableOrderPaymentResponse splitPayment(Long orderId, SplitTableOrderPaymentRequest request, String idempotencyKey) {
        bookingLock.acquire();
        String canonical = request.items() == null ? "null" : request.items().stream()
                .sorted(Comparator.comparing(SplitTableOrderItemRequest::itemId))
                .map(item -> item.itemId() + ":" + item.quantity())
                .reduce((left, right) -> left + "," + right).orElse("");
        String fingerprint = org.thomcgn.backend.common.application.IdempotencyKeys.fingerprint(canonical);
        var replay = replay(idempotencyKey, "SPLIT_PAYMENT", orderId, fingerprint);
        if (replay.isPresent()) {
            if (replay.get().getResultOrder() == null) throw new ConflictException("Stored split result is incomplete");
            return new SplitTableOrderPaymentResponse(toResponse(replay.get().getOrder()), toResponse(replay.get().getResultOrder()));
        }
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
            if (splitEntry.getValue() == null || splitEntry.getValue() < 1)
                throw new BadRequestException("Split quantity must be positive");
            if (splitEntry.getValue() > existingItem.getQuantity()) {
                throw new BadRequestException("Split quantity exceeds existing quantity for item: " + splitEntry.getKey());
            }
        }

        LocalDateTime now = businessSettings.currentVenueTime();
        TableOrder paidOrder = new TableOrder();
        paidOrder.setTable(openOrder.getTable());
        paidOrder.setReservation(openOrder.getReservation());
        paidOrder.setStatus(TableOrderStatus.CLOSED);
        paidOrder.setPaid(true);
        paidOrder.setOpenedAt(now);
        paidOrder.setClosedAt(now);
        paidOrder.setClosedBusinessDate(businessSettings.getCurrentBusinessDate());
        TableOrder savedPaidOrder = orderRepository.save(paidOrder);

        for (Map.Entry<Long, Integer> splitEntry : requestedQuantitiesByItem.entrySet()) {
            TableOrderItem openItem = openItemsById.get(splitEntry.getKey());
            int paidQuantity = splitEntry.getValue();
            BigDecimal unitDeductedVolume = openItem.getDeductedVolumeMl().divide(
                    BigDecimal.valueOf(openItem.getQuantity()),
                    4,
                    RoundingMode.HALF_UP
            );

            TableOrderItem paidItem = new TableOrderItem();
            paidItem.setTableOrder(savedPaidOrder);
            paidItem.setDrinkVariant(openItem.getDrinkVariant());
            paidItem.setQuantity(paidQuantity);
            paidItem.setSaleBusinessDate(openItem.getSaleBusinessDate());
            paidItem.setUnitPrice(openItem.getUnitPrice());
            paidItem.setTotalPrice(Money.multiply(openItem.getUnitPrice(), paidQuantity));
            paidItem.setDeductedVolumeMl(unitDeductedVolume.multiply(BigDecimal.valueOf(paidQuantity)).setScale(4, RoundingMode.HALF_UP));
            itemRepository.save(paidItem);

            int remainingQuantity = openItem.getQuantity() - paidQuantity;
            if (remainingQuantity <= 0) {
                itemRepository.delete(openItem);
            } else {
                openItem.setQuantity(remainingQuantity);
                openItem.setTotalPrice(Money.multiply(openItem.getUnitPrice(), remainingQuantity));
                openItem.setDeductedVolumeMl(unitDeductedVolume.multiply(BigDecimal.valueOf(remainingQuantity)).setScale(4, RoundingMode.HALF_UP));
                itemRepository.save(openItem);
            }
        }

        boolean hasRemainingOpenItems = !itemRepository.findByTableOrderId(openOrder.getId()).isEmpty();
        TableOrder persistedOpenOrder = openOrder;
        if (!hasRemainingOpenItems) {
            openOrder.setStatus(TableOrderStatus.CLOSED);
            openOrder.setPaid(true);
            openOrder.setClosedAt(now);
            openOrder.setClosedBusinessDate(businessSettings.getCurrentBusinessDate());
            persistedOpenOrder = orderRepository.saveAndFlush(openOrder);
            releaseAfterClose(persistedOpenOrder);
        }

        recordOperation(idempotencyKey, "SPLIT_PAYMENT", persistedOpenOrder, fingerprint, savedPaidOrder);
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

    private Optional<BillingOperation> replay(String rawKey, String type, Long orderId, String fingerprint) {
        String key = org.thomcgn.backend.common.application.IdempotencyKeys.optional(rawKey);
        if (key == null) return Optional.empty();
        return operationRepository.findByOperationKey(key).map(operation -> {
            if (!operation.getOperationType().equals(type)
                    || !operation.getOrder().getId().equals(orderId)
                    || !operation.getRequestFingerprint().equals(fingerprint)) {
                throw new ConflictException("Idempotency-Key was already used for a different command");
            }
            return operation;
        });
    }

    private void recordOperation(String rawKey, String type, TableOrder order, String fingerprint, TableOrder result) {
        String key = org.thomcgn.backend.common.application.IdempotencyKeys.optional(rawKey);
        if (key == null) return;
        BillingOperation operation = new BillingOperation();
        operation.setOperationKey(key);
        operation.setOperationType(type);
        operation.setOrder(order);
        operation.setRequestFingerprint(fingerprint);
        operation.setResultOrder(result);
        operationRepository.save(operation);
    }

    private TableOrderResponse stateOperationResponse(String key, String type, TableOrder order) {
        recordOperation(key, type, order, "state", null);
        return toResponse(order);
    }

    private void releaseAfterClose(TableOrder order) {
        if (order.getSaleType() == SaleType.DIRECT) return;
        var table = order.getTable();
        table.setStatus(orderRepository.hasOpenOrders(table.getId()) ? TableStatus.OCCUPIED : TableStatus.FREE);
        tableRepository.save(table);
        events.publishEvent(new org.thomcgn.backend.billing.application.TableVisitEnded(
                order.getReservation() == null ? null : order.getReservation().getId()));
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
                .map(this::toItemResponseSafely)
                .toList();

        BigDecimal total = itemRepository.getTotalByTableOrderId(order.getId());

        return new TableOrderResponse(
                order.getId(),
                resolveTableId(order),
                order.getSaleType(),
                order.getPaymentMethod(),
                resolveTableName(order),
                resolveReservationId(order),
                order.getStatus(),
                order.isPaid(),
                order.getOpenedAt(),
                order.getClosedAt(),
                total,
                itemResponses
        );
    }

    private TableOrderItemResponse toItemResponseSafely(TableOrderItem item) {
        try {
            return new TableOrderItemResponse(
                    item.getId(),
                    item.getDrinkVariant().getId(),
                    item.getDrinkVariant().getDrink().getName() + " " + item.getDrinkVariant().getDisplayVolumeName(),
                    item.getQuantity(),
                    item.getUnitPrice(),
                    item.getTotalPrice(),
                    item.getDeductedVolumeMl()
            );
        } catch (RuntimeException ignored) {
            return new TableOrderItemResponse(
                    item.getId(),
                    null,
                    "Geloeschte Variante",
                    item.getQuantity(),
                    item.getUnitPrice(),
                    item.getTotalPrice(),
                    item.getDeductedVolumeMl()
            );
        }
    }

    private Long resolveTableId(TableOrder order) {
        try {
            return order.getTable() != null ? order.getTable().getId() : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String resolveTableName(TableOrder order) {
        if (order.getSaleType() == SaleType.DIRECT) return "Barverkauf";
        try {
            return order.getTable() != null ? order.getTable().getName() : "Unbekannter Tisch";
        } catch (RuntimeException ignored) {
            return "Unbekannter Tisch";
        }
    }

    private Long resolveReservationId(TableOrder order) {
        try {
            return order.getReservation() != null ? order.getReservation().getId() : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}

