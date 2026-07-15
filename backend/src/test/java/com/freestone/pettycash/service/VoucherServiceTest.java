package com.freestone.pettycash.service;

import com.freestone.pettycash.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class VoucherServiceTest {

    @Autowired
    private VoucherService voucherService;

    @Test
    @DisplayName("VoucherService should build a valid non-empty PDF byte array")
    void generateTransactionVoucherSucceeds() {
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
        assertThat(pdfBytes.length).isGreaterThan(100); // PDF header itself is more than 100 bytes

        // PDF signature validation (%PDF-1.4 or similar at offset 0)
        String pdfHeader = new String(pdfBytes, 0, 4);
        assertThat(pdfHeader).isEqualTo("%PDF");
    }
}
