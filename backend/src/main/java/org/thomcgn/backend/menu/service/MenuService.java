package org.thomcgn.backend.menu.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thomcgn.backend.common.exception.NotFoundException;
import org.thomcgn.backend.menu.api.dto.DrinkCategoryRequest;
import org.thomcgn.backend.menu.api.dto.DrinkCategoryResponse;
import org.thomcgn.backend.menu.api.dto.DrinkRequest;
import org.thomcgn.backend.menu.api.dto.DrinkResponse;
import org.thomcgn.backend.menu.api.dto.DrinkVariantRequest;
import org.thomcgn.backend.menu.api.dto.DrinkVariantResponse;
import org.thomcgn.backend.menu.domain.Drink;
import org.thomcgn.backend.menu.domain.DrinkCategory;
import org.thomcgn.backend.menu.domain.DrinkVariant;
import org.thomcgn.backend.menu.repository.DrinkCategoryRepository;
import org.thomcgn.backend.menu.repository.DrinkRepository;
import org.thomcgn.backend.menu.repository.DrinkVariantRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MenuService {

    private final DrinkCategoryRepository categoryRepository;
    private final DrinkRepository drinkRepository;
    private final DrinkVariantRepository variantRepository;

    @Transactional(readOnly = true)
    public List<DrinkCategoryResponse> listCategories() {
        return categoryRepository.findAllByOrderBySortOrderAscNameAsc().stream()
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
        return drinkRepository.findAllByOrderByNameAsc().stream()
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
        drinkRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<DrinkVariantResponse> listVariants() {
        return variantRepository.findAllByOrderByDrink_NameAscDisplayVolumeNameAsc().stream()
                .map(variant -> new DrinkVariantResponse(
                        variant.getId(),
                        variant.getDrink().getId(),
                        variant.getDrink().getName(),
                        variant.getDisplayVolumeName(),
                        variant.getVolumeMl(),
                        variant.getPrice(),
                        variant.getSku(),
                        variant.isActive()
                )).toList();
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
        return toResponse(variantRepository.save(variant));
    }

    @Transactional
    public void deleteVariant(Long id) {
        variantRepository.deleteById(id);
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
        variant.setDrink(drink);
        variant.setDisplayVolumeName(request.displayVolumeName().trim());
        variant.setVolumeMl(request.volumeMl());
        variant.setPrice(request.price());
        variant.setSku(request.sku());
        variant.setActive(request.active());
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
                variant.getPrice(),
                variant.getSku(),
                variant.isActive()
        );
    }
}


