package org.thomcgn.backend.report.service;

import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Service;
import org.thomcgn.backend.inventory.api.dto.ReorderSuggestionResponse;
import org.thomcgn.backend.inventory.service.InventoryService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReorderReportService {

    private final InventoryService inventoryService;

    public byte[] generateReorderPdf(String supplier) {
        String normalizedSupplier = normalizeSupplier(supplier);
        List<ReorderSuggestionResponse> suggestions = inventoryService.getReorderSuggestions().stream()
                .filter(item -> normalizedSupplier == null || normalizedSupplier.equalsIgnoreCase(normalizeSupplier(item.supplier())))
                .toList();

        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            try (var fontStream = ReorderReportService.class.getResourceAsStream("/fonts/DejaVuSans.ttf")) {
                if (fontStream == null) throw new IOException("Report font missing");
                PDType0Font font = PDType0Font.load(document, fontStream);
                try (var pages = new ReportPages(document, font)) {
                    pages.line("Datum: " + LocalDate.now());
                    if (normalizedSupplier != null) pages.line("Lieferant: " + normalizedSupplier);
                    pages.line("Artikel | Bestand | Schwelle | Empfehlung | Gebinde | Lieferant");
                    for (ReorderSuggestionResponse item : suggestions) {
                        pages.line(String.format(
                                "%s | %s %s | %s %s | %s %s (~%s %s) | %s %s | %s",
                                item.inventoryItemName(), item.currentStock(), item.contentUnit(),
                                item.threshold(), item.contentUnit(), item.recommendedOrderAmount(), item.contentUnit(),
                                item.recommendedOrderPackages(), item.packageType(), item.packageSize(), item.contentUnit(),
                                item.supplier() != null ? item.supplier() : "-"));
                    }
                }
            }

            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to generate reorder PDF", exception);
        }
    }

    private static final class ReportPages implements AutoCloseable {
        private final PDDocument document;
        private final PDType0Font font;
        private PDPageContentStream content;
        private float y;

        ReportPages(PDDocument document, PDType0Font font) {
            this.document = document;
            this.font = font;
        }

        void line(String text) throws IOException {
            StringBuilder line = new StringBuilder();
            float width = 0;
            for (int codePoint : text.codePoints().toArray()) {
                String glyph = Character.isISOControl(codePoint) ? " " : new String(Character.toChars(codePoint));
                float glyphWidth;
                try { glyphWidth = font.getStringWidth(glyph) * 9 / 1000; }
                catch (IllegalArgumentException unsupported) {
                    // Explicit readable fallback for characters outside the embedded font.
                    glyph = "?";
                    glyphWidth = font.getStringWidth(glyph) * 9 / 1000;
                }
                if (width + glyphWidth > PDRectangle.A4.getWidth() - 100 && !line.isEmpty()) {
                    write(line.toString());
                    line.setLength(0);
                    width = 0;
                }
                line.append(glyph);
                width += glyphWidth;
            }
            write(line.toString());
        }

        private void write(String text) throws IOException {
            if (content == null || y < 50) {
                if (content != null) content.close();
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                content = new PDPageContentStream(document, page);
                content.setFont(font, 12);
                draw("FassWerk - Nachbestellliste / Seite " + document.getNumberOfPages(), 790);
                content.setFont(font, 9);
                y = 768;
            }
            draw(text, y);
            y -= 12;
        }

        private void draw(String text, float baseline) throws IOException {
            content.beginText();
            content.newLineAtOffset(50, baseline);
            content.showText(text);
            content.endText();
        }

        @Override public void close() throws IOException {
            if (content != null) content.close();
        }
    }

    private String normalizeSupplier(String supplier) {
        if (supplier == null) {
            return null;
        }
        String normalized = supplier.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}

