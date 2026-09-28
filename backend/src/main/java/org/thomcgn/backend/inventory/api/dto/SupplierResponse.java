package org.thomcgn.backend.inventory.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonProperty;

@Schema(description = "Supplier Response contract")
public record SupplierResponse(
    @JsonProperty("id")
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,

    @JsonProperty("name")
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,

    @JsonProperty("contactEmail")
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String contactEmail,

    @JsonProperty("contactPhone")
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String contactPhone,

    @JsonProperty("website")
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String website,

    @JsonProperty("notes")
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String notes,

    @JsonProperty("active")
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean active
) {}


