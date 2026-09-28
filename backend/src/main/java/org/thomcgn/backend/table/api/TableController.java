package org.thomcgn.backend.table.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.thomcgn.backend.table.api.dto.TableRequest;
import org.thomcgn.backend.table.api.dto.TableResponse;
import org.thomcgn.backend.table.service.TableService;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/tables")
@Tag(name = "Tables", description = "Tischstammdaten, Kapazitaet und Belegungsstatus")
@SecurityRequirement(name = "bearerAuth")
public class TableController {

    private final TableService tableService;

    @GetMapping
    @Operation(summary = "Tische listen")
    public List<TableResponse> list() {
        return tableService.list();
    }

    @PostMapping
    @Operation(summary = "Tisch anlegen")
    @ResponseStatus(HttpStatus.CREATED)
    public TableResponse create(@Valid @RequestBody TableRequest request) {
        return tableService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Tisch aktualisieren")
    public TableResponse update(@PathVariable Long id, @Valid @RequestBody TableRequest request) {
        return tableService.update(id, request);
    }
}

