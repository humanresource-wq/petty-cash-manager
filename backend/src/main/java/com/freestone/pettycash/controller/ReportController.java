package com.freestone.pettycash.controller;

import com.freestone.pettycash.model.PettyCashTransaction;
import com.freestone.pettycash.repository.TransactionRepository;
import com.freestone.pettycash.service.CustomReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final TransactionRepository transactionRepository;
    private final CustomReportService customReportService;

    @GetMapping("/export/pdf")
    public ResponseEntity<byte[]> exportPdf(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) String company,
            @RequestParam(required = false) String categoryName
    ) {
        List<PettyCashTransaction> list = transactionRepository.findFilteredList(
                startDate, endDate, company != null && !company.isBlank() ? company.trim() : null,
                categoryName != null && !categoryName.isBlank() ? categoryName.trim() : null);

        byte[] pdfBytes = customReportService.generatePdfCustomReport(
                list, startDate, endDate, company, categoryName);

        String filename = "petty_cash_report_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".pdf";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    @GetMapping("/export/csv")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) String company,
            @RequestParam(required = false) String categoryName
    ) {
        List<PettyCashTransaction> list = transactionRepository.findFilteredList(
                startDate, endDate, company != null && !company.isBlank() ? company.trim() : null,
                categoryName != null && !categoryName.isBlank() ? categoryName.trim() : null);

        byte[] csvBytes = customReportService.generateCsvCustomReport(
                list, startDate, endDate, company, categoryName);

        String filename = "petty_cash_report_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csvBytes);
    }
}
