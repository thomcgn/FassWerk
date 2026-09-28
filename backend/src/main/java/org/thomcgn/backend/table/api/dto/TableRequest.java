package org.thomcgn.backend.table.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.thomcgn.backend.table.domain.TableStatus;

public record TableRequest(
        @NotBlank String name,
        String area,
        @NotNull TableStatus status,
        @NotNull Boolean active,
        @Min(1) Integer seats
) {
    public TableRequest(String name, String area, TableStatus status, Boolean active) {
        this(name, area, status, active, null);
    }
}

