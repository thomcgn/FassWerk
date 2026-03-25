package org.thomcgn.backend.menu.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thomcgn.backend.billing.domain.TableOrderItem;
import org.thomcgn.backend.billing.domain.TableOrderStatus;
import org.thomcgn.backend.billing.repository.TableOrderItemRepository;
import org.thomcgn.backend.common.exception.BadRequestException;
import org.thomcgn.backend.common.exception.NotFoundException;
import org.thomcgn.backend.inventory.domain.InventoryItem;
import org.thomcgn.backend.inventory.repository.InventoryItemRepository;
import org.thomcgn.backend.menu.api.dto.DrinkCategoryRequest;
import org.thomcgn.backend.menu.api.dto.DrinkCategoryResponse;
import org.thomcgn.backend.menu.api.dto.DrinkRequest;
import org.thomcgn.backend.menu.api.dto.DrinkResponse;
import org.thomcgn.backend.menu.api.dto.DrinkVariantRequest;
import org.thomcgn.backend.menu.api.dto.DrinkVariantResponse;
import org.thomcgn.backend.menu.api.dto.VolumePriceRequest;
import org.thomcgn.backend.menu.api.dto.VolumePriceResponse;
import org.thomcgn.backend.menu.api.dto.VolumePriceUpdateRequest;
import org.thomcgn.backend.menu.domain.Drink;
import org.thomcgn.backend.menu.domain.DrinkCategory;
import org.thomcgn.backend.menu.domain.DrinkVariant;
import org.thomcgn.backend.menu.domain.VolumePrice;
import org.thomcgn.backend.menu.repository.DrinkCategoryRepository;
import org.thomcgn.backend.menu.repository.DrinkRepository;
import org.thomcgn.backend.menu.repository.DrinkVariantRepository;
import org.thomcgn.backend.menu.repository.VolumePriceRepository;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MenuService {

    private final DrinkCategoryRepository categoryRepository;
    private final DrinkRepository drinkRepository;
    private final DrinkVariantRepository variantRepository;
    private final VolumePriceRepository volumePriceRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final TableOrderItemRepository tableOrderItemRepository;

    @Transactional(readOnly = true)
    public List<DrinkCategoryResponse> listCategories() {
        return categoryRepository.findAllByActiveTrueOrderBySortOrderAscNameAsc().stream()
                .map(category -> new DrinkCategoryResponse(category.getId(), category.getName(), category.getSortOrder(), category.isActive()))
                .toList();
    }

    @Transactional
    public DrinkCategoryResponse createCategory(DrinkCategoryRequest request) {
        DrinkCategory category = new DrinkCategory();
        applyCategory(category, request);
        return toResponse(categoryRepository.save(category));
    }

    @Transactional
    public DrinkCategoryResponse updateCategory(Long id, DrinkCategoryRequest request) {
        DrinkCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Drink category not found: " + id));
        applyCategory(category, request);
        return toResponse(categoryRepository.save(category));
    }

    @Transactional
    public void deleteCategory(Long id) {
        categoryRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<DrinkResponse> listDrinks() {
        return drinkRepository.findAllByActiveTrueOrderByNameAsc().stream()
                .map(drink -> new DrinkResponse(
                        drink.getId(),
                        drink.getCategory().getId(),
                        drink.getCategory().getName(),
                        drink.getName(),
                        drink.getDescription(),
                        drink.getImageUrl(),
                        drink.isActive()
                )).toList();
    }

    @Transactional
    public DrinkResponse createDrink(DrinkRequest request) {
        Drink drink = new Drink();
        applyDrink(drink, request);
        return toResponse(drinkRepository.save(drink));
    }

    @Transactional
    public DrinkResponse updateDrink(Long id, DrinkRequest request) {
        Drink drink = drinkRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Drink not found: " + id));
        applyDrink(drink, request);
        return toResponse(drinkRepository.save(drink));
    }

    @Transactional
    public void deleteDrink(Long id) {
        Drink drink = drinkRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Drink not found: " + id));

        List<DrinkVariant> variants = variantRepository.findByDrinkId(id);
        List<Long> variantIds = variants.stream().map(DrinkVariant::getId).toList();
        Map<Long, InventoryItem> inventoryItemsToUnlink = new LinkedHashMap<>();

        for (Long variantId : variantIds) {
            tableOrderItemRepository.deleteByDrinkVariantId(variantId);
            inventoryItemRepository.findAllByLinkedDrinkVariantId(variantId)
                    .forEach(item -> inventoryItemsToUnlink.put(item.getId(), item));
        }

        inventoryItemRepository.findAllByLinkedDrinkId(id)
                .forEach(item -> inventoryItemsToUnlink.put(item.getId(), item));

        for (InventoryItem inventoryItem : inventoryItemsToUnlink.values()) {
            if (inventoryItem.getLinkedDrink() != null && id.equals(inventoryItem.getLinkedDrink().getId())) {
                inventoryItem.setLinkedDrink(null);
            }
            if (inventoryItem.getLinkedDrinkVariant() != null && variantIds.contains(inventoryItem.getLinkedDrinkVariant().getId())) {
                inventoryItem.setLinkedDrinkVariant(null);
            }
            inventoryItemRepository.save(inventoryItem);
        }

        for (DrinkVariant variant : variants) {
            variantRepository.delete(variant);
        }

        drinkRepository.delete(drink);
    }

    @Transactional(readOnly = true)
    public List<DrinkVariantResponse> listVariants() {
        return variantRepository.findAllByActiveTrueOrderByDrink_NameAscDisplayVolumeNameAsc().stream()
                .map(variant -> new DrinkVariantResponse(
                        variant.getId(),
                        variant.getDrink().getId(),
                        variant.getDrink().getName(),
                        variant.getDisplayVolumeName(),
                        variant.getVolumeMl(),
                        resolveVariantPrice(variant),
                        variant.isUseVolumeStandardPrice(),
                        variant.getSku(),
                        variant.isActive()
                )).toList();
    }

    @Transactional(readOnly = true)
    public List<VolumePriceResponse> listVolumePrices() {
        return volumePriceRepository.findAllByOrderByVolumeMlAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public VolumePriceResponse createOrUpdateVolumePrice(VolumePriceRequest request) {
        VolumePrice volumePrice = volumePriceRepository.findByVolumeMl(request.volumeMl())
                .orElseGet(VolumePrice::new);
        volumePrice.setVolumeMl(request.volumeMl());
        volumePrice.setPrice(request.price());
        VolumePrice saved = volumePriceRepository.save(volumePrice);
        syncVariantPrices(request.volumeMl(), request.price());
        return toResponse(saved);
    }

    @Transactional
    public VolumePriceResponse updateVolumePrice(Integer volumeMl, VolumePriceUpdateRequest request) {
        VolumePrice volumePrice = volumePriceRepository.findByVolumeMl(volumeMl)
                .orElseThrow(() -> new NotFoundException("Volume price not found for volumeMl: " + volumeMl));
        volumePrice.setPrice(request.price());
        VolumePrice saved = volumePriceRepository.save(volumePrice);
        syncVariantPrices(volumeMl, request.price());
        return toResponse(saved);
    }

    @Transactional
    public void deleteVolumePrice(Integer volumeMl) {
        VolumePrice volumePrice = volumePriceRepository.findByVolumeMl(volumeMl)
                .orElseThrow(() -> new NotFoundException("Volume price not found for volumeMl: " + volumeMl));
        volumePriceRepository.delete(volumePrice);
    }

    @Transactional
    public DrinkVariantResponse createVariant(DrinkVariantRequest request) {
        DrinkVariant variant = new DrinkVariant();
        applyVariant(variant, request);
        return toResponse(variantRepository.save(variant));
    }

    @Transactional
    public DrinkVariantResponse updateVariant(Long id, DrinkVariantRequest request) {
        DrinkVariant variant = variantRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Drink variant not found: " + id));
        applyVariant(variant, request);
        DrinkVariant saved = variantRepository.save(variant);
        syncOpenOrderItemsForVariantPrice(saved.getId(), resolveVariantPrice(saved));
        return toResponse(saved);
    }

    @Transactional
    public void deleteVariant(Long id) {
        DrinkVariant variant = variantRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Drink variant not found: " + id));

        tableOrderItemRepository.deleteByDrinkVariantId(id);

        inventoryItemRepository.findAllByLinkedDrinkVariantId(id)
                .forEach(item -> {
                    item.setLinkedDrinkVariant(null);
                    inventoryItemRepository.save(item);
                });

        variantRepository.delete(variant);
    }

    private void applyCategory(DrinkCategory category, DrinkCategoryRequest request) {
        category.setName(request.name().trim());
        category.setSortOrder(request.sortOrder());
        category.setActive(request.active());
    }

    private void applyDrink(Drink drink, DrinkRequest request) {
        DrinkCategory category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new NotFoundException("Drink category not found: " + request.categoryId()));
        drink.setCategory(category);
        drink.setName(request.name().trim());
        drink.setDescription(request.description());
        drink.setImageUrl(request.imageUrl());
        drink.setActive(request.active());
    }

    private void applyVariant(DrinkVariant variant, DrinkVariantRequest request) {
        Drink drink = drinkRepository.findById(request.drinkId())
                .orElseThrow(() -> new NotFoundException("Drink not found: " + request.drinkId()));
        BigDecimal resolvedPrice = resolvePriceForRequest(variant, request);

        variant.setDrink(drink);
        variant.setDisplayVolumeName(request.displayVolumeName().trim());
        variant.setVolumeMl(request.volumeMl());
        variant.setPrice(resolvedPrice);
        variant.setSku(request.sku());
        variant.setActive(request.active());
    }

    private BigDecimal resolvePriceForRequest(DrinkVariant variant, DrinkVariantRequest request) {
        BigDecimal globalPrice = volumePriceRepository.findByVolumeMl(request.volumeMl())
                .map(VolumePrice::getPrice)
                .orElse(null);
        boolean isCreate = variant.getId() == null;
        BigDecimal requestPrice = request.price();

        if (Boolean.TRUE.equals(request.useStandardPrice())) {
            if (globalPrice == null) {
                throw new BadRequestException("No global price configured for volumeMl: " + request.volumeMl());
            }
            variant.setUseVolumeStandardPrice(true);
            return globalPrice;
        }

        if (Boolean.FALSE.equals(request.useStandardPrice())) {
            if (requestPrice == null) {
                throw new BadRequestException("Custom price is required when useStandardPrice is false");
            }
            variant.setUseVolumeStandardPrice(false);
            return requestPrice;
        }

        if (requestPrice != null) {
            variant.setUseVolumeStandardPrice(false);
            return requestPrice;
        }

        if (isCreate || variant.isUseVolumeStandardPrice()) {
            if (globalPrice == null) {
                throw new BadRequestException("No global price configured for volumeMl: " + request.volumeMl());
            }
            variant.setUseVolumeStandardPrice(true);
            return globalPrice;
        }

        return variant.getPrice();
    }

    private BigDecimal resolvePriceForVolume(Integer volumeMl, BigDecimal fallbackPrice) {
        return volumePriceRepository.findByVolumeMl(volumeMl)
                .map(VolumePrice::getPrice)
                .orElse(fallbackPrice);
    }

    private BigDecimal resolveVariantPrice(DrinkVariant variant) {
        if (variant.isUseVolumeStandardPrice()) {
            return resolvePriceForVolume(variant.getVolumeMl(), variant.getPrice());
        }
        return variant.getPrice();
    }

    private void syncVariantPrices(Integer volumeMl, BigDecimal price) {
        variantRepository.findAllByVolumeMlAndUseVolumeStandardPriceTrue(volumeMl).forEach(variant -> {
            variant.setPrice(price);
            DrinkVariant saved = variantRepository.save(variant);
            syncOpenOrderItemsForVariantPrice(saved.getId(), price);
        });
    }

    private void syncOpenOrderItemsForVariantPrice(Long variantId, BigDecimal unitPrice) {
        List<TableOrderItem> openItems = tableOrderItemRepository.findAllByDrinkVariantIdAndTableOrderStatus(
                variantId,
                TableOrderStatus.OPEN
        );
        for (TableOrderItem openItem : openItems) {
            openItem.setUnitPrice(unitPrice);
            openItem.setTotalPrice(unitPrice.multiply(BigDecimal.valueOf(openItem.getQuantity())));
            tableOrderItemRepository.save(openItem);
        }
    }

    private DrinkCategoryResponse toResponse(DrinkCategory category) {
        return new DrinkCategoryResponse(category.getId(), category.getName(), category.getSortOrder(), category.isActive());
    }

    private DrinkResponse toResponse(Drink drink) {
        return new DrinkResponse(
                drink.getId(),
                drink.getCategory().getId(),
                drink.getCategory().getName(),
                drink.getName(),
                drink.getDescription(),
                drink.getImageUrl(),
                drink.isActive()
        );
    }

    private DrinkVariantResponse toResponse(DrinkVariant variant) {
        return new DrinkVariantResponse(
                variant.getId(),
                variant.getDrink().getId(),
                variant.getDrink().getName(),
                variant.getDisplayVolumeName(),
                variant.getVolumeMl(),
                resolveVariantPrice(variant),
                variant.isUseVolumeStandardPrice(),
                variant.getSku(),
                variant.isActive()
        );
    }

    private VolumePriceResponse toResponse(VolumePrice volumePrice) {
        return new VolumePriceResponse(volumePrice.getId(), volumePrice.getVolumeMl(), volumePrice.getPrice());
    }
}


