package com.freestone.pettycash.service;

import com.freestone.pettycash.model.*;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class VoucherServiceTest {

    @Autowired
    private VoucherService voucherService;

    @Test
    @DisplayName("VoucherService should build a valid 1-page PDF voucher without Status field")
    void generateTransactionVoucherSucceedsAndFitsOnePage() throws IOException {
        Category category = new Category("Travel");
        Subcategory subcategory = new Subcategory("Auto", category);

        PettyCashTransaction transaction = new PettyCashTransaction(
                "TX-2026-9999",
                TransactionType.EXPENSE,
                BigDecimal.valueOf(150.00),
                "Auto ride to client office",
                LocalDate.now(),
                "harshada.surve@freestoneinfotech.com",
                "Local Driver",
                category,
                subcategory
        );
        transaction.setVoucherNumber("Voc-001-09-12-2026");
        transaction.setCompany("Freestone Technologies LLP");

        byte[] pdfBytes = voucherService.generateTransactionVoucher(transaction);

        assertThat(pdfBytes).isNotNull();
        assertThat(pdfBytes.length).isGreaterThan(100);

        // Header check
        String pdfHeader = new String(pdfBytes, 0, 4);
        assertThat(pdfHeader).isEqualTo("%PDF");

        // Verify page count is strictly 1
        PdfReader reader = new PdfReader(pdfBytes);
        assertThat(reader.getNumberOfPages()).isEqualTo(1);

        // Extract text and verify "Status:" field is removed
        PdfTextExtractor extractor = new PdfTextExtractor(reader);
        String textOnPage1 = extractor.getTextFromPage(1);
        assertThat(textOnPage1).doesNotContain("Status:");
        assertThat(textOnPage1).contains("Prepared By");
        assertThat(textOnPage1).contains("Received By");
        assertThat(textOnPage1).contains("Approved By");

        reader.close();
    }
}
