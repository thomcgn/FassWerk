package org.thomcgn.backend.inventory.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thomcgn.backend.common.exception.ConflictException;
import org.thomcgn.backend.inventory.config.InventoryDefaultsProperties;
import org.thomcgn.backend.inventory.api.dto.InventoryItemRequest;
import org.thomcgn.backend.inventory.domain.ContentUnit;
import org.thomcgn.backend.inventory.domain.InventoryItem;
import org.thomcgn.backend.inventory.domain.PackageType;
import org.thomcgn.backend.inventory.repository.InventoryItemRepository;
import org.thomcgn.backend.inventory.repository.InventoryMovementRepository;
import org.thomcgn.backend.menu.domain.Drink;
import org.thomcgn.backend.menu.domain.DrinkVariant;
import org.thomcgn.backend.menu.repository.DrinkRepository;
import org.thomcgn.backend.menu.repository.DrinkVariantRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceUnitConversionTest {

    @Mock
    private InventoryItemRepository inventoryItemRepository;

    @Mock
    private InventoryMovementRepository movementRepository;

    @Mock
    private DrinkRepository drinkRepository;

    @Mock
    private DrinkVariantRepository drinkVariantRepository;

    @Mock
    private InventoryDefaultsProperties inventoryDefaultsProperties;

    @InjectMocks
    private InventoryService inventoryService;

    @Test
    void deductForOrderItem_convertsMlToLiterBeforeStockCheck() {
        DrinkVariant variant = createVariant(2L);
        InventoryItem item = createInventoryItem(ContentUnit.LITER, "50.00");

        when(inventoryItemRepository.findFirstByLinkedDrinkVariantIdAndActiveTrue(variant.getId()))
                .thenReturn(Optional.of(item));
        when(inventoryItemRepository.save(any(InventoryItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        inventoryService.deductForOrderItem(variant, new BigDecimal("800"), "order-item-1");

        assertEquals(0, item.getTotalStockAmount().compareTo(new BigDecimal("49.20")));
    }

    @Test
    void deductForOrderItem_keepsMlLogicForMilliliterInventory() {
        DrinkVariant variant = createVariant(2L);
        InventoryItem item = createInventoryItem(ContentUnit.MILLILITER, "50000.00");

        when(inventoryItemRepository.findFirstByLinkedDrinkVariantIdAndActiveTrue(variant.getId()))
                .thenReturn(Optional.of(item));
        when(inventoryItemRepository.save(any(InventoryItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        inventoryService.deductForOrderItem(variant, new BigDecimal("800"), "order-item-2");

        assertEquals(0, item.getTotalStockAmount().compareTo(new BigDecimal("49200.00")));
    }

    @Test
    void deductForOrderItem_rejectsPieceUnitForDrinkVolumeDeduction() {
        DrinkVariant variant = createVariant(2L);
        InventoryItem item = createInventoryItem(ContentUnit.PIECE, "100.00");

        when(inventoryItemRepository.findFirstByLinkedDrinkVariantIdAndActiveTrue(variant.getId()))
                .thenReturn(Optional.of(item));

        ConflictException exception = assertThrows(
                ConflictException.class,
                () -> inventoryService.deductForOrderItem(variant, new BigDecimal("800"), "order-item-3")
        );
        assertEquals(
                "Lagerartikel ist auf STUECK konfiguriert und kann nicht ueber Getraenkevolumen abgebucht werden",
                exception.getMessage()
        );
    }

    @Test
    void deductForOrderItem_fallsBackToDrinkLinkWhenVariantLinkMissing() {
        DrinkVariant variant = createVariant(7L);
        InventoryItem item = createInventoryItem(ContentUnit.LITER, "20.00");

        when(inventoryItemRepository.findFirstByLinkedDrinkVariantIdAndActiveTrue(variant.getId()))
                .thenReturn(Optional.empty());
        when(inventoryItemRepository.findAllByLinkedDrinkIdAndActiveTrue(11L))
                .thenReturn(List.of(item));
        when(inventoryItemRepository.save(any(InventoryItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        inventoryService.deductForOrderItem(variant, new BigDecimal("500"), "order-item-4");

        assertEquals(0, item.getTotalStockAmount().compareTo(new BigDecimal("19.50")));
    }

    @Test
    void createItem_usesPackageBasedThresholdFields() {
        InventoryItemRequest request = new InventoryItemRequest(
                "Guinness Fass",
                null,
                null,
                PackageType.BARREL,
                new BigDecimal("3"),
                new BigDecimal("50"),
                ContentUnit.LITER,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("1"),
                new BigDecimal("2"),
                new BigDecimal("2"),
                "Beispiel Lieferant",
                true
        );

        when(inventoryItemRepository.save(any(InventoryItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = inventoryService.createItem(request);

        assertEquals(new BigDecimal("50"), response.reorderThreshold());
        assertEquals(new BigDecimal("100"), response.minimumStock());
        assertEquals(new BigDecimal("100"), response.recommendedReorderAmount());
    }

    @Test
    void createItem_treatsZeroLinkIdsAsUnlinked() {
        InventoryItemRequest request = new InventoryItemRequest(
                "Unverknuepfter Lagerartikel",
                0L,
                0L,
                PackageType.BOX,
                new BigDecimal("5"),
                new BigDecimal("1"),
                ContentUnit.PIECE,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                null,
                null,
                null,
                true
        );

        when(inventoryItemRepository.save(any(InventoryItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = inventoryService.createItem(request);

        assertNull(response.linkedDrinkId());
        assertNull(response.linkedDrinkVariantId());
    }

    @Test
    void deleteItem_deletesMovementsBeforeInventoryItem() {
        InventoryItem item = createInventoryItem(ContentUnit.LITER, "10.00");
        item.setId(77L);

        when(inventoryItemRepository.findById(77L)).thenReturn(Optional.of(item));

        inventoryService.deleteItem(77L);

        var inOrder = inOrder(movementRepository, inventoryItemRepository);
        inOrder.verify(movementRepository).deleteByInventoryItemId(77L);
        inOrder.verify(inventoryItemRepository).delete(item);
        verify(inventoryItemRepository).findById(77L);
    }

    private DrinkVariant createVariant(Long id) {
        Drink drink = new Drink();
        drink.setId(11L);
        drink.setActive(true);

        DrinkVariant variant = new DrinkVariant();
        variant.setId(id);
        variant.setDrink(drink);
        variant.setActive(true);
        variant.setVolumeMl(500);
        variant.setPrice(new BigDecimal("4.00"));
        variant.setDisplayVolumeName("0.5l");
        return variant;
    }

    private InventoryItem createInventoryItem(ContentUnit unit, String stockAmount) {
        InventoryItem item = new InventoryItem();
        item.setId(99L);
        item.setActive(true);
        item.setContentUnit(unit);
        item.setTotalStockAmount(new BigDecimal(stockAmount));
        item.setPackageType(PackageType.BARREL);
        item.setContentPerPackage(BigDecimal.ONE);
        item.setPackagesInStock(BigDecimal.ONE);
        item.setMinimumStock(BigDecimal.ZERO);
        item.setReorderThreshold(BigDecimal.ZERO);
        item.setRecommendedReorderAmount(BigDecimal.ZERO);
        item.setName("Testbestand");
        return item;
    }
}

