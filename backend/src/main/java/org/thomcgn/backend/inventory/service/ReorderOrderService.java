package org.thomcgn.backend.inventory.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thomcgn.backend.common.exception.NotFoundException;
import org.thomcgn.backend.inventory.api.dto.ReorderOrderRequest;
import org.thomcgn.backend.inventory.api.dto.ReorderOrderResponse;
import org.thomcgn.backend.inventory.api.dto.SupplierRequest;
import org.thomcgn.backend.inventory.api.dto.SupplierResponse;
import org.thomcgn.backend.inventory.domain.InventoryItem;
import org.thomcgn.backend.inventory.domain.ReorderOrder;
import org.thomcgn.backend.inventory.domain.Supplier;
import org.thomcgn.backend.inventory.repository.InventoryItemRepository;
import org.thomcgn.backend.inventory.repository.ReorderOrderRepository;
import org.thomcgn.backend.inventory.repository.SupplierRepository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReorderOrderService {

    private final ReorderOrderRepository reorderOrderRepository;
    private final SupplierRepository supplierRepository;
    private final InventoryItemRepository inventoryItemRepository;

    // =========== Supplier Management ===========

    @Transactional
    public SupplierResponse createSupplier(SupplierRequest request) {
        Supplier supplier = new Supplier();
        supplier.setName(request.name());
        supplier.setContactEmail(request.contactEmail());
        supplier.setContactPhone(request.contactPhone());
        supplier.setWebsite(request.website());
        supplier.setNotes(request.notes());
        supplier.setActive(request.active() != null ? request.active() : true);

        Supplier saved = supplierRepository.save(supplier);
        return toSupplierResponse(saved);
    }

    public List<SupplierResponse> getAllSuppliers(boolean onlyActive) {
        List<Supplier> suppliers = onlyActive
                ? supplierRepository.findByActiveTrue()
                : supplierRepository.findAll();
        return suppliers.stream().map(this::toSupplierResponse).toList();
    }

    @Transactional
    public SupplierResponse updateSupplier(Long supplierId, SupplierRequest request) {
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new NotFoundException("Supplier not found: " + supplierId));

        if (request.name() != null) supplier.setName(request.name());
        if (request.contactEmail() != null) supplier.setContactEmail(request.contactEmail());
        if (request.contactPhone() != null) supplier.setContactPhone(request.contactPhone());
        if (request.website() != null) supplier.setWebsite(request.website());
        if (request.notes() != null) supplier.setNotes(request.notes());
        if (request.active() != null) supplier.setActive(request.active());

        Supplier saved = supplierRepository.save(supplier);
        return toSupplierResponse(saved);
    }

    // =========== Reorder Order Management ===========

    @Transactional
    public ReorderOrderResponse createReorderOrder(ReorderOrderRequest request) {
        InventoryItem item = inventoryItemRepository.findById(request.inventoryItemId())
                .orElseThrow(() -> new NotFoundException("Inventory item not found: " + request.inventoryItemId()));

        Supplier supplier = supplierRepository.findById(request.supplierId())
                .orElseThrow(() -> new NotFoundException("Supplier not found: " + request.supplierId()));

        ReorderOrder order = new ReorderOrder();
        order.setInventoryItem(item);
        order.setSupplier(supplier);
        order.setOrderedQuantity(request.orderedQuantity());
        order.setOrderedUnit(request.orderedUnit());
        order.setScheduledDeliveryDate(request.scheduledDeliveryDate());
        order.setScheduledDeliveryTime(request.scheduledDeliveryTime());
        order.setStatus(ReorderOrder.ReorderStatus.PENDING);
        order.setNotes(request.notes());

        ReorderOrder saved = reorderOrderRepository.save(order);
        log.info("Created reorder order #{} for supplier {} on {}", saved.getId(), supplier.getName(), request.scheduledDeliveryDate());

        return toReorderOrderResponse(saved);
    }

    public List<ReorderOrderResponse> getUpcomingDeliveries() {
        List<ReorderOrder> orders = reorderOrderRepository.findUpcomingDeliveries();
        return orders.stream().map(this::toReorderOrderResponse).toList();
    }

    public List<ReorderOrderResponse> getDeliveriesByDateRange(LocalDate startDate, LocalDate endDate) {
        List<ReorderOrder> orders = reorderOrderRepository.findByDateRange(startDate, endDate);
        return orders.stream().map(this::toReorderOrderResponse).toList();
    }

    public List<ReorderOrderResponse> getReordersByInventoryItem(Long inventoryItemId) {
        List<ReorderOrder> orders = reorderOrderRepository.findByInventoryItemIdOrderByScheduledDeliveryDateDesc(inventoryItemId);
        return orders.stream().map(this::toReorderOrderResponse).toList();
    }

    @Transactional
    public ReorderOrderResponse updateReorderStatus(Long reorderId, String newStatus) {
        ReorderOrder order = reorderOrderRepository.findById(reorderId)
                .orElseThrow(() -> new NotFoundException("Reorder order not found: " + reorderId));

        try {
            order.setStatus(ReorderOrder.ReorderStatus.valueOf(newStatus.toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid status: " + newStatus);
        }

        ReorderOrder saved = reorderOrderRepository.save(order);
        log.info("Updated reorder order #{} status to {}", saved.getId(), newStatus);

        return toReorderOrderResponse(saved);
    }

    // =========== Helpers ===========

    private ReorderOrderResponse toReorderOrderResponse(ReorderOrder order) {
        return new ReorderOrderResponse(
                order.getId(),
                order.getInventoryItem().getId(),
                order.getInventoryItem().getName(),
                order.getSupplier().getId(),
                order.getSupplier().getName(),
                order.getOrderedQuantity(),
                order.getOrderedUnit(),
                order.getScheduledDeliveryDate(),
                order.getScheduledDeliveryTime(),
                order.getStatus().toString(),
                order.getNotes(),
                order.getReceivedQuantity(),
                order.getReceivedAt() != null ? order.getReceivedAt().toString() : null,
                order.getCreatedBy(),
                order.getCreatedAt() != null ? order.getCreatedAt().toString() : null
        );
    }

    private SupplierResponse toSupplierResponse(Supplier supplier) {
        return new SupplierResponse(
                supplier.getId(),
                supplier.getName(),
                supplier.getContactEmail(),
                supplier.getContactPhone(),
                supplier.getWebsite(),
                supplier.getNotes(),
                supplier.isActive()
        );
    }
}


