package org.thomcgn.backend.menu.api.dto;

import java.math.BigDecimal;

public record DrinkVariantResponse(
        Long id,
        Long drinkId,
        String drinkName,
        String displayVolumeName,
        Integer volumeMl,
        BigDecimal price,
        boolean useStandardPrice,
        String sku,
        boolean active
) {
}

