package org.thomcgn.backend.menu.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.thomcgn.backend.menu.api.dto.DrinkCategoryRequest;
import org.thomcgn.backend.menu.api.dto.DrinkCategoryResponse;
import org.thomcgn.backend.menu.api.dto.DrinkRequest;
import org.thomcgn.backend.menu.api.dto.DrinkResponse;
import org.thomcgn.backend.menu.api.dto.DrinkVariantRequest;
import org.thomcgn.backend.menu.api.dto.DrinkVariantResponse;
import org.thomcgn.backend.menu.service.MenuService;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class MenuController {

    private final MenuService menuService;

    @GetMapping("/drink-categories")
    public List<DrinkCategoryResponse> listCategories() {
        return menuService.listCategories();
    }

    @PostMapping("/drink-categories")
    @ResponseStatus(HttpStatus.CREATED)
    public DrinkCategoryResponse createCategory(@Valid @RequestBody DrinkCategoryRequest request) {
        return menuService.createCategory(request);
    }

    @PutMapping("/drink-categories/{id}")
    public DrinkCategoryResponse updateCategory(@PathVariable Long id, @Valid @RequestBody DrinkCategoryRequest request) {
        return menuService.updateCategory(id, request);
    }

    @DeleteMapping("/drink-categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable Long id) {
        menuService.deleteCategory(id);
    }

    @GetMapping("/drinks")
    public List<DrinkResponse> listDrinks() {
        return menuService.listDrinks();
    }

    @PostMapping("/drinks")
    @ResponseStatus(HttpStatus.CREATED)
    public DrinkResponse createDrink(@Valid @RequestBody DrinkRequest request) {
        return menuService.createDrink(request);
    }

    @PutMapping("/drinks/{id}")
    public DrinkResponse updateDrink(@PathVariable Long id, @Valid @RequestBody DrinkRequest request) {
        return menuService.updateDrink(id, request);
    }

    @DeleteMapping("/drinks/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDrink(@PathVariable Long id) {
        menuService.deleteDrink(id);
    }

    @GetMapping("/drink-variants")
    public List<DrinkVariantResponse> listVariants() {
        return menuService.listVariants();
    }

    @PostMapping("/drink-variants")
    @ResponseStatus(HttpStatus.CREATED)
    public DrinkVariantResponse createVariant(@Valid @RequestBody DrinkVariantRequest request) {
        return menuService.createVariant(request);
    }

    @PutMapping("/drink-variants/{id}")
    public DrinkVariantResponse updateVariant(@PathVariable Long id, @Valid @RequestBody DrinkVariantRequest request) {
        return menuService.updateVariant(id, request);
    }

    @DeleteMapping("/drink-variants/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteVariant(@PathVariable Long id) {
        menuService.deleteVariant(id);
    }
}

