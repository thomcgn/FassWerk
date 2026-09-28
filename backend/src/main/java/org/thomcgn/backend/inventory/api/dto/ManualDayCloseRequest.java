package org.thomcgn.backend.inventory.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

@Schema(description = "Close the displayed business date exactly once for an idempotency key")
public record ManualDayCloseRequest(@NotNull LocalDate expectedBusinessDate) {}
