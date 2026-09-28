package org.thomcgn.backend.inventory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thomcgn.backend.common.exception.BadRequestException;
import org.thomcgn.backend.common.exception.ConflictException;
import org.thomcgn.backend.common.exception.NotFoundException;
import org.thomcgn.backend.inventory.api.dto.InventoryAdjustmentRequest;
import org.thomcgn.backend.inventory.api.dto.InventoryItemRequest;
import org.thomcgn.backend.inventory.api.dto.InventoryItemResponse;
import org.thomcgn.backend.inventory.api.dto.InventoryMovementResponse;
import org.thomcgn.backend.inventory.api.dto.InventoryPackageDefaultsResponse;
import org.thomcgn.backend.inventory.api.dto.ReorderSuggestionResponse;
import org.thomcgn.backend.inventory.config.InventoryDefaultsProperties;
import org.thomcgn.backend.inventory.domain.InventoryItem;
import org.thomcgn.backend.inventory.domain.InventoryMovement;
import org.thomcgn.backend.inventory.domain.InventoryMovementType;
import org.thomcgn.backend.inventory.domain.InventoryReferenceType;
import org.thomcgn.backend.inventory.domain.PackageType;
import org.thomcgn.backend.inventory.repository.InventoryItemRepository;
import org.thomcgn.backend.inventory.repository.InventoryMovementRepository;
import org.thomcgn.backend.menu.domain.Drink;
import org.thomcgn.backend.menu.domain.DrinkVariant;
import org.thomcgn.backend.menu.repository.DrinkRepository;
import org.thomcgn.backend.menu.repository.DrinkVariantRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryMovementRepository movementRepository;
    private final DrinkRepository drinkRepository;
    private final DrinkVariantRepository drinkVariantRepository;
    private final InventoryDefaultsProperties inventoryDefaultsProperties;
    private final DrinkSalesTrackingService drinkSalesTrackingService;
    private final ReorderCalculationService reorderCalculationService;

    @Transactional(readOnly = true)
    public List<InventoryItemResponse> listItems() {
        return inventoryItemRepository.findByActiveTrue().stream()
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .map(this::toItemResponse).toList();
    }

    @Transactional(readOnly = true)
    public InventoryItemResponse getItem(Long id) {
        return toItemResponse(findItem(id));
    }

    @Transactional
    public InventoryItemResponse createItem(InventoryItemRequest request) {
        InventoryItem item = new InventoryItem();
        applyRequest(item, request);
        InventoryItem saved = inventoryItemRepository.save(item);
        return toItemResponse(saved);
    }

    @Transactional
    public InventoryItemResponse updateItem(Long id, InventoryItemRequest request) {
        InventoryItem item = findItemForUpdate(id);
        applyRequest(item, request);
        InventoryItem saved = inventoryItemRepository.save(item);
        return toItemResponse(saved);
    }

    @Transactional
    public void deleteItem(Long id) {
        InventoryItem item = findItemForUpdate(id);
        // Löse Verknüpfung zum Getränk auf statt zu löschen
        // Dadurch wird im Bar Admin wieder die Warnung angezeigt
        item.setLinkedDrink(null);
        item.setLinkedDrinkVariant(null);
        item.setActive(false);
        inventoryItemRepository.save(item);
    }

    @Transactional
    public InventoryItemResponse adjust(Long id, InventoryAdjustmentRequest request, String actor) {
        return adjust(id, request, actor, null);
    }

    @Transactional
    public InventoryItemResponse adjust(Long id, InventoryAdjustmentRequest request, String actor, String idempotencyKey) {
        InventoryItem item = findItemForUpdate(id);
        BigDecimal delta = request.increase() ? request.amount() : request.amount().negate();
        String key = org.thomcgn.backend.common.application.IdempotencyKeys.optional(idempotencyKey);
        if (key != null) {
            var existing = movementRepository.findByOperationKey(key);
            if (existing.isPresent()) {
                var movement = existing.get();
                if (!movement.getInventoryItem().getId().equals(id)
                        || movement.getMovementType() != InventoryMovementType.ADJUSTMENT
                        || movement.getAmount().compareTo(delta) != 0
                        || !java.util.Objects.equals(movement.getReason(), request.reason())) {
                    throw new ConflictException("Idempotency-Key was already used for a different inventory command");
                }
                return toItemResponse(item);
            }
        }
        BigDecimal newStock = item.getTotalStockAmount().add(delta);
        if (newStock.compareTo(BigDecimal.ZERO) < 0) {
            throw new ConflictException("Inventory adjustment would result in negative stock");
        }

        item.setTotalStockAmount(newStock);
        synchronizePackageCount(item);
        inventoryItemRepository.save(item);

        createMovement(
                item,
                InventoryMovementType.ADJUSTMENT,
                delta,
                request.reason(),
                InventoryReferenceType.MANUAL,
                id.toString(),
                actor,
                key
        );
        return toItemResponse(item);
    }

    @Transactional(readOnly = true)
    public List<InventoryMovementResponse> listMovements() {
        return movementRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(movement -> new InventoryMovementResponse(
                        movement.getId(),
                        movement.getInventoryItem().getId(),
                        movement.getInventoryItem().getName(),
                        movement.getMovementType(),
                        movement.getAmount(),
                        movement.getUnit(),
                        movement.getReason(),
                        movement.getReferenceType(),
                        movement.getReferenceId(),
                        movement.getCreatedAt(),
                        movement.getCreatedBy()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReorderSuggestionResponse> getReorderSuggestions() {
        return inventoryItemRepository.findCriticalForReorder().stream().map(this::toReorderSuggestion).toList();
    }

    @Transactional(readOnly = true)
    public List<InventoryPackageDefaultsResponse> getPackageDefaults() {
        return Arrays.stream(PackageType.values())
                .map(packageType -> {
                    InventoryDefaultsProperties.PackageDefaults defaults = inventoryDefaultsProperties
                            .getPackageDefaults()
                            .getOrDefault(packageType, new InventoryDefaultsProperties.PackageDefaults());
                    return new InventoryPackageDefaultsResponse(
                            packageType,
                            defaults.getReorderThresholdPackages(),
                            defaults.getMinimumStockPackages(),
                            defaults.getRecommendedReorderPackages()
                    );
                })
                .toList();
    }

    @Transactional
    public InventoryItem deductForOrderItem(DrinkVariant variant, BigDecimal amountMl, String referenceId) {
        return deductForOrderItem(variant, amountMl, referenceId, null);
    }

    @Transactional
    public InventoryItem deductForOrderItem(DrinkVariant variant, BigDecimal amountMl, String referenceId,
            java.time.LocalDate saleDate) {
        InventoryItem inventoryItem = findInventoryItemForVariantForUpdate(variant);
        BigDecimal amountInInventoryUnit = convertAmountMlToInventoryUnit(amountMl, inventoryItem);
        assertAvailable(inventoryItem, amountInInventoryUnit, variant.getId());
        BigDecimal newStock = inventoryItem.getTotalStockAmount().subtract(amountInInventoryUnit);
        if (newStock.compareTo(BigDecimal.ZERO) < 0) {
            throw new ConflictException("Insufficient inventory for variant: " + variant.getId());
        }

        inventoryItem.setTotalStockAmount(newStock);
        synchronizePackageCount(inventoryItem);
        inventoryItemRepository.save(inventoryItem);

        createMovement(
                inventoryItem,
                InventoryMovementType.SALE,
                amountInInventoryUnit.negate(),
                "Sale deduction",
                InventoryReferenceType.TABLE_ORDER_ITEM,
                referenceId,
                "system"
        );

        BigDecimal quantity = amountMl.divide(BigDecimal.valueOf(variant.getVolumeMl()), 4, RoundingMode.HALF_UP);
        if (saleDate == null) recordSaleAndUpdateReorder(variant, quantity, amountMl, inventoryItem);
        else {
            drinkSalesTrackingService.recordSale(variant, quantity, amountMl, saleDate);
            reorderCalculationService.calculateReorderAmount(inventoryItem);
        }

        return inventoryItem;
    }

    @Transactional
    public InventoryItem restockForCancelledOrderItem(DrinkVariant variant, BigDecimal amountMl, String referenceId) {
        return restockForCancelledOrderItem(variant, amountMl, referenceId, null);
    }

    @Transactional
    public InventoryItem restockForCancelledOrderItem(DrinkVariant variant, BigDecimal amountMl, String referenceId,
            java.time.LocalDate originalBusinessDate) {
        InventoryItem inventoryItem = findInventoryItemForVariantForUpdate(variant);
        BigDecimal amountInInventoryUnit = convertAmountMlToInventoryUnit(amountMl, inventoryItem);
        inventoryItem.setTotalStockAmount(inventoryItem.getTotalStockAmount().add(amountInInventoryUnit));
        synchronizePackageCount(inventoryItem);
        inventoryItemRepository.save(inventoryItem);

        createMovement(
                inventoryItem,
                InventoryMovementType.ADJUSTMENT,
                amountInInventoryUnit,
                "Order item cancellation rollback",
                InventoryReferenceType.TABLE_ORDER_ITEM,
                referenceId,
                "system"
        );
        BigDecimal quantity = amountMl.divide(BigDecimal.valueOf(variant.getVolumeMl()), 4, RoundingMode.HALF_UP);
        if (originalBusinessDate == null) drinkSalesTrackingService.reverseSale(variant, quantity, amountMl);
        else drinkSalesTrackingService.reverseSale(variant, quantity, amountMl, originalBusinessDate);
        reorderCalculationService.calculateReorderAmount(inventoryItem);
        return inventoryItem;
    }

    @Transactional(readOnly = true)
    public void assertVariantAvailableForOrder(DrinkVariant variant, BigDecimal amountMl) {
        InventoryItem inventoryItem = findInventoryItemForVariant(variant);
        BigDecimal amountInInventoryUnit = convertAmountMlToInventoryUnit(amountMl, inventoryItem);
        assertAvailable(inventoryItem, amountInInventoryUnit, variant.getId());
    }

    private void assertAvailable(InventoryItem inventoryItem, BigDecimal amount, Long variantId) {
        if (!inventoryItem.isActive()) {
            throw new ConflictException("Drink variant is currently not available in inventory");
        }
        if (inventoryItem.getTotalStockAmount().compareTo(amount) < 0) {
            throw new ConflictException("Insufficient inventory for variant: " + variantId);
        }
    }

    private BigDecimal convertAmountMlToInventoryUnit(BigDecimal amountMl, InventoryItem inventoryItem) {
        return switch (inventoryItem.getContentUnit()) {
            case MILLILITER -> amountMl;
            case LITER -> amountMl.movePointLeft(3);
            case PIECE -> throw new ConflictException("Lagerartikel ist auf STUECK konfiguriert und kann nicht ueber Getraenkevolumen abgebucht werden");
        };
    }

    private InventoryItem findItem(Long id) {
        return inventoryItemRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Inventory item not found: " + id));
    }

    private InventoryItem findItemForUpdate(Long id) {
        return inventoryItemRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Inventory item not found: " + id));
    }

    public InventoryItem findInventoryItemById(Long id) {
        return findItem(id);
    }

    private InventoryItem findInventoryItemForVariant(DrinkVariant variant) {
        return inventoryItemRepository.findFirstByLinkedDrinkVariantIdAndActiveTrue(variant.getId())
                .orElseGet(() -> findInventoryItemForDrink(variant.getDrink().getId(), variant.getId()));
    }

    private InventoryItem findInventoryItemForVariantForUpdate(DrinkVariant variant) {
        return inventoryItemRepository.findActiveByVariantIdForUpdate(variant.getId())
                .orElseGet(() -> findInventoryItemForDrinkForUpdate(variant.getDrink().getId(), variant.getId()));
    }

    private InventoryItem findInventoryItemForDrinkForUpdate(Long drinkId, Long variantId) {
        List<InventoryItem> drinkItems = inventoryItemRepository.findActiveByDrinkIdForUpdate(drinkId);
        if (drinkItems.isEmpty()) {
            throw new NotFoundException("No inventory item linked to drink " + drinkId + " (variant " + variantId + ")");
        }
        if (drinkItems.size() > 1) {
            throw new ConflictException("Mehrere aktive Lagerartikel sind mit dem Drink verknuepft. Bitte Variante eindeutig verknuepfen.");
        }
        return drinkItems.getFirst();
    }

    private InventoryItem findInventoryItemForDrink(Long drinkId, Long variantId) {
        List<InventoryItem> drinkItems = inventoryItemRepository.findAllByLinkedDrinkIdAndActiveTrue(drinkId);
        if (drinkItems.isEmpty()) {
            throw new NotFoundException("No inventory item linked to drink " + drinkId + " (variant " + variantId + ")");
        }
        if (drinkItems.size() > 1) {
            throw new ConflictException("Mehrere aktive Lagerartikel sind mit dem Drink verknuepft. Bitte Variante eindeutig verknuepfen.");
        }
        return drinkItems.get(0);
    }

    private void createMovement(
            InventoryItem item,
            InventoryMovementType movementType,
            BigDecimal amount,
            String reason,
            InventoryReferenceType referenceType,
            String referenceId,
            String actor
    ) {
        createMovement(item, movementType, amount, reason, referenceType, referenceId, actor, null);
    }

    private void createMovement(
            InventoryItem item,
            InventoryMovementType movementType,
            BigDecimal amount,
            String reason,
            InventoryReferenceType referenceType,
            String referenceId,
            String actor,
            String operationKey
    ) {
        InventoryMovement movement = new InventoryMovement();
        movement.setInventoryItem(item);
        movement.setMovementType(movementType);
        movement.setAmount(amount);
        movement.setUnit(item.getContentUnit());
        movement.setReason(reason);
        movement.setReferenceType(referenceType);
        movement.setReferenceId(referenceId);
        movement.setCreatedBy(actor);
        movement.setOperationKey(operationKey);
        movementRepository.save(movement);
    }

    private void recordSaleAndUpdateReorder(
            DrinkVariant variant,
            BigDecimal quantity,
            BigDecimal amountMl,
            InventoryItem inventoryItem
    ) {
        drinkSalesTrackingService.recordSale(variant, quantity, amountMl);
        reorderCalculationService.calculateReorderAmount(inventoryItem);
    }

    private void synchronizePackageCount(InventoryItem item) {
        if (item.getContentPerPackage().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Package content must be positive");
        }
        item.setPackagesInStock(item.getTotalStockAmount().divide(item.getContentPerPackage(), 2, RoundingMode.HALF_UP));
    }

    private void applyRequest(InventoryItem item, InventoryItemRequest request) {
        Drink linkedDrink = resolveDrink(request.linkedDrinkId());
        DrinkVariant linkedVariant = resolveVariant(request.linkedDrinkVariantId());
        if (linkedVariant != null) {
            Drink variantDrink = linkedVariant.getDrink();
            if (linkedDrink != null && !variantDrink.getId().equals(linkedDrink.getId())) {
                throw new BadRequestException("Die Drink-Variante gehoert nicht zum ausgewaehlten Drink");
            }
            linkedDrink = variantDrink;
        }

        item.setName(request.name().trim());
        item.setLinkedDrink(linkedDrink);
        item.setLinkedDrinkVariant(linkedVariant);
        item.setPackageType(request.packageType());
        item.setPackagesInStock(request.packagesInStock());
        item.setContentPerPackage(request.contentPerPackage());
        item.setContentUnit(request.contentUnit());
        item.setTotalStockAmount(request.packagesInStock().multiply(request.contentPerPackage()));

        BigDecimal contentPerPackage = request.contentPerPackage();
        BigDecimal reorderThreshold = request.reorderThresholdPackages() != null
                ? request.reorderThresholdPackages().multiply(contentPerPackage)
                : request.reorderThreshold();
        BigDecimal minimumStock = request.minimumStockPackages() != null
                ? request.minimumStockPackages().multiply(contentPerPackage)
                : request.minimumStock();
        BigDecimal recommendedReorderAmount = request.minimumStockPackages() != null
            ? request.minimumStockPackages().multiply(contentPerPackage)
            : (request.recommendedReorderAmount() != null
                ? request.recommendedReorderAmount()
                : BigDecimal.ZERO);

        item.setReorderThreshold(reorderThreshold);
        item.setMinimumStock(minimumStock);
        item.setRecommendedReorderAmount(recommendedReorderAmount);
        item.setSupplier(request.supplier());
        item.setActive(request.active());
    }

    private Drink resolveDrink(Long id) {
        if (id == null || id <= 0) {
            return null;
        }
        return drinkRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Drink not found: " + id));
    }

    private DrinkVariant resolveVariant(Long id) {
        if (id == null || id <= 0) {
            return null;
        }
        return drinkVariantRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Drink variant not found: " + id));
    }

    private InventoryItemResponse toItemResponse(InventoryItem item) {
        return new InventoryItemResponse(
                item.getId(),
                item.getName(),
                item.getLinkedDrink() != null ? item.getLinkedDrink().getId() : null,
                item.getLinkedDrinkVariant() != null ? item.getLinkedDrinkVariant().getId() : null,
                item.getPackageType(),
                item.getPackagesInStock(),
                item.getContentPerPackage(),
                item.getContentUnit(),
                item.getTotalStockAmount(),
                item.getReorderThreshold(),
                item.getMinimumStock(),
                item.getRecommendedReorderAmount(),
                item.getSupplier(),
                item.isActive()
        );
    }

    private ReorderSuggestionResponse toReorderSuggestion(InventoryItem item) {
        BigDecimal targetAmount = item.getMinimumStock().add(item.getRecommendedReorderAmount());
        BigDecimal deficit = targetAmount.subtract(item.getTotalStockAmount());
        BigDecimal recommendedAmount = deficit.max(BigDecimal.ZERO);
        BigDecimal recommendedPackages = recommendedAmount.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : recommendedAmount.divide(item.getContentPerPackage(), 0, RoundingMode.CEILING);

        return new ReorderSuggestionResponse(
                item.getId(),
                item.getName(),
                item.getTotalStockAmount(),
                item.getReorderThreshold(),
                item.getMinimumStock(),
                recommendedAmount,
                recommendedPackages,
                item.getContentPerPackage(),
                item.getPackageType(),
                item.getContentUnit(),
                item.getSupplier()
        );
    }
}

