package org.thomcgn.backend.inventory.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SupplierRequest(
    @JsonProperty("name")
    String name,

    @JsonProperty("contactEmail")
    String contactEmail,

    @JsonProperty("contactPhone")
    String contactPhone,

    @JsonProperty("website")
    String website,

    @JsonProperty("notes")
    String notes,

    @JsonProperty("active")
    Boolean active
) {}


