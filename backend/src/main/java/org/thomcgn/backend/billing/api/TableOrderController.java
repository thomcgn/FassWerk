package org.thomcgn.backend.billing.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.thomcgn.backend.billing.api.dto.AddTableOrderItemRequest;
import org.thomcgn.backend.billing.api.dto.OpenTableOrderRequest;
import org.thomcgn.backend.billing.api.dto.TableOrderResponse;
import org.thomcgn.backend.billing.service.TableOrderService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/table-orders")
public class TableOrderController {

    private final TableOrderService tableOrderService;

    @PostMapping("/open")
    public TableOrderResponse open(@Valid @RequestBody OpenTableOrderRequest request) {
        return tableOrderService.open(request);
    }

    @PostMapping("/{id}/items")
    public TableOrderResponse addItem(@PathVariable Long id, @Valid @RequestBody AddTableOrderItemRequest request) {
        return tableOrderService.addItem(id, request);
    }

    @DeleteMapping("/{id}/items/{itemId}")
    public TableOrderResponse removeItem(@PathVariable Long id, @PathVariable Long itemId) {
        return tableOrderService.removeItem(id, itemId);
    }

    @PostMapping("/{id}/close")
    public TableOrderResponse close(@PathVariable Long id) {
        return tableOrderService.close(id);
    }

    @GetMapping("/{id}")
    public TableOrderResponse getById(@PathVariable Long id) {
        return tableOrderService.getById(id);
    }
}

