package org.thomcgn.backend.table.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.thomcgn.backend.table.domain.TableStatus;

@Schema(description = "Table Response contract")
public record TableResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String area,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) TableStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean active,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Integer seats
) {
}

