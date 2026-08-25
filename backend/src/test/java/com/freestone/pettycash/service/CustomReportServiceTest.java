package com.freestone.pettycash.service;

import com.freestone.pettycash.model.Category;
import com.freestone.pettycash.model.PettyCashTransaction;
import com.freestone.pettycash.model.ReceiptStatus;
import com.freestone.pettycash.model.TransactionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CustomReportServiceTest {

    @Autowired
    private CustomReportService customReportService;

    @Test
    @DisplayName("generateCsvCustomReport includes Voucher No, Vendor/Payee, Cash Received, and Monthly Cash in Hand")
    void generateCsvCustomReportHasUpdatedColumnsAndMonthlyCashInHand() {
        Category cat = new Category("Office Supplies");

        PettyCashTransaction topup = new PettyCashTransaction(
                "TX-001", TransactionType.TOPUP, BigDecimal.valueOf(10000),
                "Initial funding", LocalDate.of(2026, 7, 1),
                "admin@acme.com", "Bank Account", null, null);
        topup.setVoucherNumber("VOC-101");
        topup.setCompany("Acme Corp");

        PettyCashTransaction expense = new PettyCashTransaction(
                "TX-002", TransactionType.EXPENSE, BigDecimal.valueOf(2500),
                "Printer Paper", LocalDate.of(2026, 7, 15),
                "admin@acme.com", "Staples Vendor", cat, null);
        expense.setVoucherNumber("VOC-102");
        expense.setCompany("Acme Corp");
        expense.setReceiptStatus(ReceiptStatus.RECEIVED);

        List<PettyCashTransaction> list = List.of(topup, expense);

        byte[] csvBytes = customReportService.generateCsvCustomReport(
                list, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31),
                "Acme Corp", null, null, null);

        String csvContent = new String(csvBytes, java.nio.charset.StandardCharsets.UTF_8);

        // 1. Verify Cash Received column header (renamed from Replenishments A/C)
        assertThat(csvContent).contains("Cash Received");
        assertThat(csvContent).doesNotContain("Replenishments A/C");
        assertThat(csvContent).doesNotContain("Replenishment A/C");

        // 2. Verify Voucher No and Vendor/Payee columns in detail section
        assertThat(csvContent).contains("Date,Voucher No,Particulars,Vendor/Payee");
        assertThat(csvContent).contains("VOC-101");
        assertThat(csvContent).contains("VOC-102");
        assertThat(csvContent).contains("Staples Vendor");

        // 3. Verify Monthly Statistics has Cash in Hand column
        assertThat(csvContent).contains("Month,Total Spent (Expense),Total Added (Top-up),Cash in Hand");
        // 2026-07 stats: Spent 2500, Added 10000 -> Cash in Hand 7500
        assertThat(csvContent).contains("2026-07,2500,10000,7500");
    }

    @Test
    @DisplayName("generatePdfCustomReport generates non-empty PDF byte array")
    void generatePdfCustomReportProducesPdf() {
        Category cat = new Category("Travel");

        PettyCashTransaction expense = new PettyCashTransaction(
                "TX-003", TransactionType.EXPENSE, BigDecimal.valueOf(450),
                "Cab fare", LocalDate.of(2026, 8, 10),
                "admin@acme.com", "Uber Rides", cat, null);
        expense.setVoucherNumber("VOC-201");
        expense.setCompany("Acme Corp");

        byte[] pdfBytes = customReportService.generatePdfCustomReport(
                List.of(expense), LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                "Acme Corp", null, null, null);

        assertThat(pdfBytes).isNotEmpty();
        assertThat(pdfBytes.length).isGreaterThan(100);
    }
}
