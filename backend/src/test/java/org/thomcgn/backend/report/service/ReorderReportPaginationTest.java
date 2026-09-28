package org.thomcgn.backend.report.service;

import org.junit.jupiter.api.Test;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.thomcgn.backend.inventory.service.InventoryService;
import org.thomcgn.backend.inventory.api.dto.ReorderSuggestionResponse;
import org.thomcgn.backend.inventory.domain.ContentUnit;
import org.thomcgn.backend.inventory.domain.PackageType;
import java.math.BigDecimal;
import java.util.stream.IntStream;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class ReorderReportPaginationTest {
    @Test void longUnicodeListRetainsEveryEntryAcrossPages() throws Exception {
        var inventory = mock(InventoryService.class);
        var suggestions = IntStream.rangeClosed(1, 160).mapToObj(index -> new ReorderSuggestionResponse(
                (long) index, "Artikel-" + index + "-Ende / Łódź / Ελληνικά / " + "lang ".repeat(40),
                BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE,
                BigDecimal.TEN, PackageType.values()[0], ContentUnit.LITER, "Müller")).toList();
        when(inventory.getReorderSuggestions()).thenReturn(suggestions);
        byte[] bytes = new ReorderReportService(inventory).generateReorderPdf(null);
        try (var document = Loader.loadPDF(bytes)) {
            assertThat(document.getNumberOfPages()).isGreaterThan(2);
            String text = new PDFTextStripper().getText(document);
            for (int i = 1; i <= 160; i++) assertThat(text).contains("Artikel-" + i + "-Ende");
            assertThat(text).contains("Łódź", "Ελληνικά", "Müller");
        }
    }
}
