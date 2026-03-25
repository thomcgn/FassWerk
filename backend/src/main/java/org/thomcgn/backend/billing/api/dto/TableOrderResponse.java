package org.thomcgn.backend.billing.api.dto;

import org.thomcgn.backend.billing.domain.TableOrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record TableOrderResponse(
        Long id,
        Long tableId,
        String tableName,
        Long reservationId,
        TableOrderStatus status,
        boolean paid,
        LocalDateTime openedAt,
        LocalDateTime closedAt,
        BigDecimal total,
        List<TableOrderItemResponse> items
) {
}

