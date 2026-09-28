package org.thomcgn.backend.inventory.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonProperty;

@Schema(description = "Supplier Request contract")
public record SupplierRequest(
    @JsonProperty("name")
    @Schema(nullable = true) String name,

    @JsonProperty("contactEmail")
    @Schema(nullable = true) String contactEmail,

    @JsonProperty("contactPhone")
    @Schema(nullable = true) String contactPhone,

    @JsonProperty("website")
    @Schema(nullable = true) String website,

    @JsonProperty("notes")
    @Schema(nullable = true) String notes,

    @JsonProperty("active")
    @Schema(nullable = true) Boolean active
) {}


