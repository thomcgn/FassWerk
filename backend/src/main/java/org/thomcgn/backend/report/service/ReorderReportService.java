package org.thomcgn.backend.report.service;

import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
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
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 14);
                writeLine(content, 50, 790, "FassWerk - Nachbestellliste");

                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                writeLine(content, 50, 772, "Datum: " + LocalDate.now());
                if (normalizedSupplier != null) {
                    writeLine(content, 50, 758, "Lieferant: " + normalizedSupplier);
                }

                float y = normalizedSupplier != null ? 734 : 748;
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 10);
                writeLine(content, 50, y, "Artikel | Bestand | Schwelle | Empfehlung | Gebinde | Lieferant");
                y -= 14;

                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 9);
                for (ReorderSuggestionResponse item : suggestions) {
                    if (y < 50) {
                        break;
                    }

                    String line = String.format(
                            "%s | %s %s | %s %s | %s %s (~%s %s) | %s %s | %s",
                            item.inventoryItemName(),
                            item.currentStock(), item.contentUnit(),
                            item.threshold(), item.contentUnit(),
                            item.recommendedOrderAmount(), item.contentUnit(),
                            item.recommendedOrderPackages(), item.packageType(),
                            item.packageSize(), item.contentUnit(),
                            item.supplier() != null ? item.supplier() : "-"
                    );
                    writeLine(content, 50, y, line);
                    y -= 12;
                }
            }

            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to generate reorder PDF", exception);
        }
    }

    private void writeLine(PDPageContentStream content, float x, float y, String text) throws IOException {
        content.beginText();
        content.newLineAtOffset(x, y);
        content.showText(text);
        content.endText();
    }

    private String normalizeSupplier(String supplier) {
        if (supplier == null) {
            return null;
        }
        String normalized = supplier.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}

