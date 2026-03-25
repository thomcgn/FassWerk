package org.thomcgn.backend.table.api.dto;

import org.thomcgn.backend.table.domain.TableStatus;

public record TableResponse(
        Long id,
        String name,
        String area,
        TableStatus status,
        boolean active
) {
}

