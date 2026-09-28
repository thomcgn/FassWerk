package org.thomcgn.backend.menu.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Volume Price Response contract")
public record VolumePriceResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer volumeMl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal price
) {
}

