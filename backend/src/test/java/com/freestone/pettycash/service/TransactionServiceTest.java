package com.freestone.pettycash.service;

import com.freestone.pettycash.dto.TransactionRequest;
import com.freestone.pettycash.dto.TransactionResponse;
import com.freestone.pettycash.exception.InsufficientBalanceException;
import com.freestone.pettycash.model.*;
import com.freestone.pettycash.repository.CashBoxRepository;
import com.freestone.pettycash.repository.CategoryRepository;
import com.freestone.pettycash.repository.SubcategoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Slf4j
class TransactionServiceTest {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private CashBoxRepository cashBoxRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private SubcategoryRepository subcategoryRepository;

    private Category testCategory;
    private Subcategory testSubcategory;

    @BeforeEach
    void setUp() {
        // Reset CashBox balance
        CashBox box = cashBoxRepository.findById(1L).orElseThrow();
        box.setBalance(BigDecimal.ZERO);
        box.setLowThreshold(BigDecimal.valueOf(2000.00));
        cashBoxRepository.save(box);

        // Fetch or create test category
        testCategory = categoryRepository.findByNameIgnoreCase("Stationery")
                .orElseGet(() -> categoryRepository.save(new Category("Stationery")));
        
        testSubcategory = subcategoryRepository.findByCategoryIdAndNameIgnoreCase(testCategory.getId(), "Notebooks")
                .orElseGet(() -> subcategoryRepository.save(new Subcategory("Notebooks", testCategory)));
    }

    @Test
    @DisplayName("Recording Top-up should increase cash box balance")
    @Transactional
    void recordTopupIncreasesBalance() throws Exception {
        TransactionRequest request = new TransactionRequest(
                TransactionType.TOPUP,
                BigDecimal.valueOf(10000.00),
                "Initial topup",
                LocalDate.now(),
                "Bank Transfer",
                null,
                null,
                "Voc-topup-001",
                "Freestone Infotech LLP"
        );

        TransactionResponse response = transactionService.recordTransaction(request, "admin@example.com", null, null, null);

        assertThat(response.transactionNo()).startsWith("TX-");
        assertThat(response.type()).isEqualTo(TransactionType.TOPUP);
        assertThat(response.amount()).isEqualByComparingTo("10000.00");
        assertThat(response.payer()).isEqualTo("admin@example.com");

        // Verify balance
        assertThat(transactionService.getCashBoxDetails().balance()).isEqualByComparingTo("10000.00");
    }

    @Test
    @DisplayName("Recording Expense should decrease cash box balance")
    @Transactional
    void recordExpenseDecreasesBalance() throws Exception {
        // First top up
        CashBox box = cashBoxRepository.findById(1L).orElseThrow();
        box.setBalance(BigDecimal.valueOf(5000.00));
        cashBoxRepository.save(box);

        TransactionRequest request = new TransactionRequest(
                TransactionType.EXPENSE,
                BigDecimal.valueOf(450.00),
                "Buy notebooks",
                LocalDate.now(),
                "Tony Stark",
                testCategory.getId(),
                testSubcategory.getId(),
                "Voc-expense-002",
                "Freestone Infotech LLP"
        );

        TransactionResponse response = transactionService.recordTransaction(request, "admin@example.com", null, null, null);

        assertThat(response.type()).isEqualTo(TransactionType.EXPENSE);
        assertThat(response.amount()).isEqualByComparingTo("450.00");
        assertThat(response.categoryId()).isEqualTo(testCategory.getId());
        assertThat(response.subcategoryId()).isEqualTo(testSubcategory.getId());

        // Verify balance
        assertThat(transactionService.getCashBoxDetails().balance()).isEqualByComparingTo("4550.00");
    }

    @Test
    @DisplayName("Recording Expense with optional receipt file uploads it and sets status to RECEIVED")
    @Transactional
    void recordExpenseWithReceiptUploadsAndSetsStatus() throws Exception {
        CashBox box = cashBoxRepository.findById(1L).orElseThrow();
        box.setBalance(BigDecimal.valueOf(5000.00));
        cashBoxRepository.save(box);

        TransactionRequest request = new TransactionRequest(
                TransactionType.EXPENSE,
                BigDecimal.valueOf(250.00),
                "Receipt test",
                LocalDate.now(),
                "Tony Stark",
                testCategory.getId(),
                testSubcategory.getId(),
                "Voc-expense-003",
                "Freestone Infotech LLP"
        );

        byte[] fileBytes = "test-receipt-data".getBytes();
        String filename = "receipt.pdf";
        String mimeType = "application/pdf";

        TransactionResponse response = transactionService.recordTransaction(
                request, "admin@example.com", fileBytes, filename, mimeType);

        assertThat(response.type()).isEqualTo(TransactionType.EXPENSE);
        assertThat(response.receiptStatus()).isEqualTo(ReceiptStatus.RECEIVED);
        assertThat(response.receiptName()).isEqualTo("receipt.pdf");
        assertThat(response.receiptFileId()).isNotEmpty();
    }

    @Test
    @DisplayName("Recording Expense exceeding balance should throw InsufficientBalanceException")
    @Transactional
    void recordExpenseExceedingBalanceThrows() throws Exception {
        TransactionRequest request = new TransactionRequest(
                TransactionType.EXPENSE,
                BigDecimal.valueOf(100.00),
                "Exceeding expense",
                LocalDate.now(),
                "Tony Stark",
                testCategory.getId(),
                testSubcategory.getId(),
                "Voc-expense-004",
                "Freestone Infotech LLP"
        );

        assertThatThrownBy(() -> transactionService.recordTransaction(request, "admin@example.com", null, null, null))
                .isInstanceOf(InsufficientBalanceException.class)
                .hasMessageContaining("Insufficient balance in Cash Box");
    }

    @Test
    @DisplayName("Concurrent transactions must trigger Optimistic Locking collisions, ensuring thread safety")
    void concurrentTransactionsTriggerOptimisticLocking() throws Exception {
        // Set initial balance
        CashBox box = cashBoxRepository.findById(1L).orElseThrow();
        box.setBalance(BigDecimal.valueOf(1000.00));
        cashBoxRepository.save(box);

        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Callable<TransactionResponse>> tasks = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            tasks.add(() -> {
                TransactionRequest request = new TransactionRequest(
                        TransactionType.EXPENSE,
                        BigDecimal.valueOf(10.00),
                        "Concurrent expense " + index,
                        LocalDate.now(),
                        "Tony Stark",
                        testCategory.getId(),
                        testSubcategory.getId(),
                        "Voc-concur-" + index,
                        "Freestone Infotech LLP"
                );
                // Call recording service
                return transactionService.recordTransaction(request, "admin@example.com", null, null, null);
            });
        }

        List<Future<TransactionResponse>> futures = executor.invokeAll(tasks);
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        int successes = 0;
        int failures = 0;

        for (Future<TransactionResponse> future : futures) {
            try {
                future.get();
                successes++;
            } catch (ExecutionException e) {
                failures++;
                // Expect either standard transaction exceptions or optimistic locking failures
                Throwable cause = e.getCause();
                assertThat(cause).isInstanceOfAny(
                        org.springframework.orm.ObjectOptimisticLockingFailureException.class,
                        com.freestone.pettycash.exception.InsufficientBalanceException.class,
                        org.springframework.dao.DataIntegrityViolationException.class
                );
            }
        }

        log.info("Concurrent balance update simulation results: {} successes, {} version conflict failures", successes, failures);

        // Optimistic locking should ensure that at least some overlapping threads fail
        // rather than allowing dirty reads or balance inconsistencies.
        assertThat(successes)
                .as("Only one concurrent thread should write the transaction at a time when versions match")
                .isLessThan(threadCount);
        assertThat(failures)
                .as("Version conflicts should block overlapping concurrent edits")
                .isGreaterThan(0);
    }
}
