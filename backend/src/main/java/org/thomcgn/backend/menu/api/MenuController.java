package org.thomcgn.backend.menu.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.thomcgn.backend.menu.api.dto.VolumePriceRequest;
import org.thomcgn.backend.menu.api.dto.VolumePriceResponse;
import org.thomcgn.backend.menu.api.dto.VolumePriceUpdateRequest;
import org.thomcgn.backend.menu.service.MenuService;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
@Tag(name = "Menu", description = "Bar-Administration fuer Kategorien, Getraenke, Varianten und Volumenpreise")
@SecurityRequirement(name = "bearerAuth")
public class MenuController {

    private final MenuService menuService;

    @GetMapping("/drink-categories")
    @Operation(summary = "Getraenkekategorien auflisten")
    @ApiResponse(responseCode = "200", description = "Kategorien geladen")
    public List<DrinkCategoryResponse> listCategories() {
        return menuService.listCategories();
    }

    @PostMapping("/drink-categories")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Getraenkekategorie anlegen")
    @ApiResponse(responseCode = "201", description = "Kategorie erstellt")
    public DrinkCategoryResponse createCategory(@Valid @RequestBody DrinkCategoryRequest request) {
        return menuService.createCategory(request);
    }

    @PutMapping("/drink-categories/{id}")
    @Operation(summary = "Getraenkekategorie aktualisieren")
    @ApiResponse(responseCode = "200", description = "Kategorie aktualisiert")
    public DrinkCategoryResponse updateCategory(@PathVariable Long id, @Valid @RequestBody DrinkCategoryRequest request) {
        return menuService.updateCategory(id, request);
    }

    @DeleteMapping("/drink-categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Getraenkekategorie loeschen")
    @ApiResponse(responseCode = "204", description = "Kategorie geloescht")
    public void deleteCategory(@PathVariable Long id) {
        menuService.deleteCategory(id);
    }

    @GetMapping("/drinks")
    @Operation(summary = "Getraenke auflisten")
    @ApiResponse(responseCode = "200", description = "Getraenke geladen")
    public List<DrinkResponse> listDrinks() {
        return menuService.listDrinks();
    }

    @PostMapping("/drinks")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Getraenk anlegen")
    @ApiResponse(responseCode = "201", description = "Getraenk erstellt")
    public DrinkResponse createDrink(@Valid @RequestBody DrinkRequest request) {
        return menuService.createDrink(request);
    }

    @PutMapping("/drinks/{id}")
    @Operation(summary = "Getraenk aktualisieren")
    @ApiResponse(responseCode = "200", description = "Getraenk aktualisiert")
    public DrinkResponse updateDrink(@PathVariable Long id, @Valid @RequestBody DrinkRequest request) {
        return menuService.updateDrink(id, request);
    }

    @DeleteMapping("/drinks/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Getraenk loeschen")
    @ApiResponse(responseCode = "204", description = "Getraenk geloescht")
    public void deleteDrink(@PathVariable Long id) {
        menuService.deleteDrink(id);
    }

    @GetMapping("/drink-variants")
    @Operation(summary = "Getraenkevarianten auflisten")
    @ApiResponse(responseCode = "200", description = "Varianten geladen")
    public List<DrinkVariantResponse> listVariants() {
        return menuService.listVariants();
    }

    @PostMapping("/drink-variants")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Getraenkevariante anlegen")
    @ApiResponse(responseCode = "201", description = "Variante erstellt")
    public DrinkVariantResponse createVariant(@Valid @RequestBody DrinkVariantRequest request) {
        return menuService.createVariant(request);
    }

    @PutMapping("/drink-variants/{id}")
    @Operation(summary = "Getraenkevariante aktualisieren")
    @ApiResponse(responseCode = "200", description = "Variante aktualisiert")
    public DrinkVariantResponse updateVariant(@PathVariable Long id, @Valid @RequestBody DrinkVariantRequest request) {
        return menuService.updateVariant(id, request);
    }

    @DeleteMapping("/drink-variants/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Getraenkevariante loeschen")
    @ApiResponse(responseCode = "204", description = "Variante geloescht")
    public void deleteVariant(@PathVariable Long id) {
        menuService.deleteVariant(id);
    }

    @GetMapping("/volume-prices")
    @Operation(summary = "Volumenpreise auflisten")
    @ApiResponse(responseCode = "200", description = "Volumenpreise geladen")
    public List<VolumePriceResponse> listVolumePrices() {
        return menuService.listVolumePrices();
    }

    @PostMapping("/volume-prices")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Volumenpreis anlegen oder aktualisieren")
    @ApiResponse(responseCode = "201", description = "Volumenpreis erstellt oder aktualisiert")
    public VolumePriceResponse createVolumePrice(@Valid @RequestBody VolumePriceRequest request) {
        return menuService.createOrUpdateVolumePrice(request);
    }

    @PutMapping("/volume-prices/{volumeMl}")
    @Operation(summary = "Volumenpreis fuer eine ml-Groesse aktualisieren")
    @ApiResponse(responseCode = "200", description = "Volumenpreis aktualisiert")
    public VolumePriceResponse updateVolumePrice(
            @PathVariable Integer volumeMl,
            @Valid @RequestBody VolumePriceUpdateRequest request
    ) {
        return menuService.updateVolumePrice(volumeMl, request);
    }

    @DeleteMapping("/volume-prices/{volumeMl}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Volumenpreis fuer eine ml-Groesse loeschen")
    @ApiResponse(responseCode = "204", description = "Volumenpreis geloescht")
    public void deleteVolumePrice(@PathVariable Integer volumeMl) {
        menuService.deleteVolumePrice(volumeMl);
    }
}

