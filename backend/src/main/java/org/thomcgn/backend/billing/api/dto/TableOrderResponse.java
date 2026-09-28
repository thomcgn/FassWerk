package org.thomcgn.backend.billing.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.thomcgn.backend.billing.domain.TableOrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Table Order Response contract")
public record TableOrderResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long tableId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String tableName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Long reservationId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) TableOrderStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean paid,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDateTime openedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) LocalDateTime closedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal total,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<TableOrderItemResponse> items
) {
}

