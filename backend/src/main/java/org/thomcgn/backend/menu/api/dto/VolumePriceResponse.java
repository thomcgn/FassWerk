package org.thomcgn.backend.menu.api.dto;

import java.math.BigDecimal;

public record VolumePriceResponse(
        Long id,
        Integer volumeMl,
        BigDecimal price
) {
}

