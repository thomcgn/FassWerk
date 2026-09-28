package org.thomcgn.backend.table.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.thomcgn.backend.table.domain.TableStatus;

@Schema(description = "Table Request contract")
public record TableRequest(
        @NotBlank String name,
        @Schema(nullable = true) String area,
        @NotNull TableStatus status,
        @NotNull Boolean active,
        @Schema(nullable = true)
        @Min(1) Integer seats
) {
    public TableRequest(String name, String area, TableStatus status, Boolean active) {
        this(name, area, status, active, null);
    }
}

