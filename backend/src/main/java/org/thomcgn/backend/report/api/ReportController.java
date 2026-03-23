package org.thomcgn.backend.report.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.thomcgn.backend.report.service.ReorderReportService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reports")
public class ReportController {

    private final ReorderReportService reorderReportService;

    @GetMapping(value = "/reorder-list.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> reorderListPdf() {
        byte[] payload = reorderReportService.generateReorderPdf();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=reorder-list.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(payload);
    }
}

