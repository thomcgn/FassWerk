package org.thomcgn.backend.report.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.thomcgn.backend.report.api.dto.RevenueOverviewResponse;
import org.thomcgn.backend.report.service.ReorderReportService;
import org.thomcgn.backend.report.service.RevenueReportService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reports")
public class ReportController {

    private final ReorderReportService reorderReportService;
    private final RevenueReportService revenueReportService;

    @GetMapping(value = "/reorder-list.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> reorderListPdf(@RequestParam(required = false) String supplier) {
        byte[] payload = reorderReportService.generateReorderPdf(supplier);
        String fileName = (supplier == null || supplier.isBlank())
                ? "reorder-list.pdf"
                : "reorder-list-" + supplier.trim().replaceAll("[^a-zA-Z0-9-_]", "-") + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=" + fileName)
                .contentType(MediaType.APPLICATION_PDF)
                .body(payload);
    }

    @GetMapping("/revenue-overview")
    public RevenueOverviewResponse revenueOverview() {
        return revenueReportService.getOverview();
    }
}

