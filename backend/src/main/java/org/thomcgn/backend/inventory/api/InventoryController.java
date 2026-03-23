package org.thomcgn.backend.inventory.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.thomcgn.backend.inventory.api.dto.InventoryAdjustmentRequest;
import org.thomcgn.backend.inventory.api.dto.InventoryItemRequest;
import org.thomcgn.backend.inventory.api.dto.InventoryItemResponse;
import org.thomcgn.backend.inventory.api.dto.InventoryMovementResponse;
import org.thomcgn.backend.inventory.api.dto.ReorderSuggestionResponse;
import org.thomcgn.backend.inventory.service.InventoryService;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping
    public List<InventoryItemResponse> list() {
        return inventoryService.listItems();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryItemResponse create(@Valid @RequestBody InventoryItemRequest request) {
        return inventoryService.createItem(request);
    }

    @PutMapping("/{id}")
    public InventoryItemResponse update(@PathVariable Long id, @Valid @RequestBody InventoryItemRequest request) {
        return inventoryService.updateItem(id, request);
    }

    @PostMapping("/{id}/adjust")
    public InventoryItemResponse adjust(
            @PathVariable Long id,
            @Valid @RequestBody InventoryAdjustmentRequest request,
            Authentication authentication
    ) {
        String actor = authentication != null ? authentication.getName() : "system";
        return inventoryService.adjust(id, request, actor);
    }

    @GetMapping("/movements")
    public List<InventoryMovementResponse> movements() {
        return inventoryService.listMovements();
    }

    @GetMapping("/reorder-suggestions")
    public List<ReorderSuggestionResponse> reorderSuggestions() {
        return inventoryService.getReorderSuggestions();
    }
}

