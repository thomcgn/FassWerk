package org.thomcgn.backend.table.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.thomcgn.backend.table.domain.TableStatus;

public record TableRequest(
        @NotBlank String name,
        @NotNull @Min(1) Integer capacity,
        String area,
        @NotNull TableStatus status,
        @NotNull Boolean active
) {
}

