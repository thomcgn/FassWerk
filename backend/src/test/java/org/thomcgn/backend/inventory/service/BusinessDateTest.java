package org.thomcgn.backend.inventory.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import org.thomcgn.backend.inventory.domain.InventoryBusinessSettings;
import org.thomcgn.backend.inventory.repository.InventoryBusinessSettingsRepository;
import java.time.*;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class BusinessDateTest {
    @ParameterizedTest
    @CsvSource({
        "2026-03-29T00:59:59Z,2026-03-28", "2026-03-29T01:00:00Z,2026-03-28",
        "2026-03-29T02:59:59Z,2026-03-28", "2026-03-29T03:00:00Z,2026-03-29",
        "2026-10-25T00:30:00Z,2026-10-24", "2026-10-25T01:30:00Z,2026-10-24",
        "2026-10-25T03:59:59Z,2026-10-24", "2026-10-25T04:00:00Z,2026-10-25"
    })
    void nightBoundaryUsesVenueTimeIncludingBothDstTransitions(String instant, String expected) {
        var settings = settings();
        var service = service(settings, instant);
        assertThat(service.getCurrentBusinessDate()).isEqualTo(LocalDate.parse(expected));
        assertThat(service.currentVenueTime()).isEqualTo(LocalDateTime.ofInstant(Instant.parse(instant), ZoneId.of("Europe/Berlin")));
    }
    @Test void manualDateOverridesWallClock() {
        var settings = settings();
        settings.setManualBusinessDate(LocalDate.of(2035, 1, 1));
        assertThat(service(settings, "2026-03-29T03:00:00Z").getCurrentBusinessDate()).isEqualTo(LocalDate.of(2035, 1, 1));
    }
    private InventoryBusinessSettings settings() {
        var result = new InventoryBusinessSettings();
        result.setBusinessTimezone("Europe/Berlin"); result.setBusinessDayEndsAt(LocalTime.of(5, 0));
        return result;
    }
    private SalesConfigurationService service(InventoryBusinessSettings settings, String instant) {
        var repository = mock(InventoryBusinessSettingsRepository.class);
        when(repository.findTopByOrderByIdAsc()).thenReturn(Optional.of(settings));
        return new SalesConfigurationService(repository, Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }
}
