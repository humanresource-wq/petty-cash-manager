package com.freestone.pettycash.controller;

import com.freestone.pettycash.dto.CashBoxResponse;
import com.freestone.pettycash.dto.DashboardStatsResponse;
import com.freestone.pettycash.dto.TransactionRequest;
import com.freestone.pettycash.dto.TransactionResponse;
import com.freestone.pettycash.model.PettyCashTransaction;
import com.freestone.pettycash.model.ReceiptStatus;
import com.freestone.pettycash.model.Role;
import com.freestone.pettycash.model.TransactionType;
import com.freestone.pettycash.security.UserPrincipal;
import com.freestone.pettycash.service.ReportService;
import com.freestone.pettycash.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.data.domain.Page;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;
    private final ReportService reportService;

    @GetMapping
    public ResponseEntity<Page<TransactionResponse>> listTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) String categoryName,
            @RequestParam(required = false) String search
    ) {
        Page<TransactionResponse> result = transactionService.getPaginatedTransactions(
                page, size, startDate, endDate, type, categoryName, search);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/dashboard-stats")
    public ResponseEntity<DashboardStatsResponse> getDashboardStats() {
        return ResponseEntity.ok(transactionService.getDashboardStats());
    }

    @GetMapping("/export/csv")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) String categoryName,
            @RequestParam(required = false) ReceiptStatus receiptStatus,
            @RequestParam(required = false) String search
    ) {
        List<PettyCashTransaction> list = transactionService.getFilteredTransactions(
                startDate, endDate, type, categoryName, receiptStatus, search);
        byte[] csvBytes = reportService.generateCsvReport(list);

        String filename = "transactions-report-" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(csvBytes);
    }

    @GetMapping("/export/pdf")
    public ResponseEntity<byte[]> exportPdf(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) String categoryName,
            @RequestParam(required = false) ReceiptStatus receiptStatus,
            @RequestParam(required = false) String search
    ) {
        List<PettyCashTransaction> list = transactionService.getFilteredTransactions(
                startDate, endDate, type, categoryName, receiptStatus, search);
        byte[] pdfBytes = reportService.generatePdfSummaryReport(list, startDate, endDate);

        String filename = "ledger-summary-" + LocalDate.now() + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(pdfBytes);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TransactionResponse> recordTransaction(
            @RequestPart("request") @Valid TransactionRequest request,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal) throws IOException {
        
        // Only ADMINs are allowed to top up the cashbox
        if (request.type() == TransactionType.TOPUP && principal.getUser().getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Only administrators are allowed to top up the cashbox.");
        }

        byte[] fileBytes = null;
        String filename = null;
        String mimeType = null;

        if (file != null && !file.isEmpty()) {
            fileBytes = file.getBytes();
            filename = file.getOriginalFilename();
            mimeType = file.getContentType();
        }

        return ResponseEntity.ok(transactionService.recordTransaction(request, principal.getUser().getEmail(), fileBytes, filename, mimeType));
    }

    @PostMapping(value = "/{id}/receipt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TransactionResponse> uploadReceipt(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) throws IOException {
        
        byte[] bytes = file.getBytes();
        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "receipt.bin";
        String contentType = file.getContentType() != null ? file.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE;

        return ResponseEntity.ok(transactionService.uploadReceipt(id, originalFilename, bytes, contentType));
    }

    @GetMapping("/{id}/receipt")
    public ResponseEntity<byte[]> downloadReceipt(@PathVariable Long id) throws IOException {
        byte[] bytes = transactionService.downloadReceipt(id);
        
        // We will return the receipt bytes with a generic attachment content disposition
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"receipt-" + id + "\"")
                .body(bytes);
    }

    @GetMapping("/{id}/voucher")
    public ResponseEntity<byte[]> downloadVoucher(@PathVariable Long id) throws IOException {
        byte[] bytes = transactionService.getOrGenerateVoucher(id);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"voucher-" + id + ".pdf\"")
                .body(bytes);
    }

    @PutMapping("/{id}/receipt-status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TransactionResponse> updateReceiptStatus(
            @PathVariable Long id,
            @RequestParam ReceiptStatus status) {
        return ResponseEntity.ok(transactionService.toggleReceiptReceived(id, status));
    }

    @GetMapping("/cashbox")
    public ResponseEntity<CashBoxResponse> getCashBoxDetails() {
        return ResponseEntity.ok(transactionService.getCashBoxDetails());
    }

    @PutMapping("/cashbox/threshold")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CashBoxResponse> updateThreshold(@RequestParam BigDecimal threshold) {
        return ResponseEntity.ok(transactionService.updateLowThreshold(threshold));
    }
}
