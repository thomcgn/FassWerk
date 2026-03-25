package org.thomcgn.backend.inventory.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.thomcgn.backend.inventory.domain.PackageType;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.inventory")
public class InventoryDefaultsProperties {

    private Map<PackageType, PackageDefaults> packageDefaults = new EnumMap<>(PackageType.class);

    @Getter
    @Setter
    public static class PackageDefaults {
        private BigDecimal reorderThresholdPackages = BigDecimal.ZERO;
        private BigDecimal minimumStockPackages = BigDecimal.ZERO;
        private BigDecimal recommendedReorderPackages = BigDecimal.ZERO;
    }
}

