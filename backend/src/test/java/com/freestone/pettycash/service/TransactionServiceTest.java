package com.freestone.pettycash.service;

import com.freestone.pettycash.dto.TransactionRequest;
import com.freestone.pettycash.dto.TransactionResponse;
import com.freestone.pettycash.dto.TransactionUpdateRequest;
import com.freestone.pettycash.dto.DashboardStatsResponse;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import org.springframework.jdbc.core.JdbcTemplate;

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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

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
                "Freestone Technologies LLP"
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
                "Freestone Technologies LLP"
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
                "Freestone Technologies LLP"
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
                "Freestone Technologies LLP"
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
                        "Freestone Technologies LLP"
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

    // ─── updateTransaction tests ────────────────────────────────────────────

    @Test
    @DisplayName("Updating expense amount upwards deducts more from cashbox")
    @Transactional
    void updateExpenseAmountUpwardsReducesBalance() throws Exception {
        // Setup: cashbox with 5000, record 200 expense → balance 4800
        CashBox box = cashBoxRepository.findById(1L).orElseThrow();
        box.setBalance(BigDecimal.valueOf(5000.00));
        cashBoxRepository.save(box);

        TransactionRequest createReq = new TransactionRequest(
                TransactionType.EXPENSE,
                BigDecimal.valueOf(200.00),
                "Original expense",
                LocalDate.now(),
                "John",
                testCategory.getId(),
                null,
                "Voc-upd-001",
                "Freestone Technologies LLP"
        );
        TransactionResponse created = transactionService.recordTransaction(createReq, "admin@example.com", null, null, null);
        assertThat(transactionService.getCashBoxDetails().balance()).isEqualByComparingTo("4800.00");

        // Edit: raise amount to 500 → balance should go from 4800 → (4800+200-500) = 4500
        TransactionUpdateRequest updateReq = new TransactionUpdateRequest(
                BigDecimal.valueOf(500.00),
                "Updated expense",
                LocalDate.now(),
                "John",
                testCategory.getId(),
                null,
                "Voc-upd-001",
                "Freestone Technologies LLP"
        );
        TransactionResponse updated = transactionService.updateTransaction(created.id(), updateReq);
        assertThat(updated.amount()).isEqualByComparingTo("500.00");
        assertThat(updated.description()).isEqualTo("Updated expense");
        assertThat(transactionService.getCashBoxDetails().balance()).isEqualByComparingTo("4500.00");
    }

    @Test
    @DisplayName("Updating expense amount downwards returns difference to cashbox")
    @Transactional
    void updateExpenseAmountDownwardsIncreasesBalance() throws Exception {
        // Setup: cashbox 3000, record 1000 expense → balance 2000
        CashBox box = cashBoxRepository.findById(1L).orElseThrow();
        box.setBalance(BigDecimal.valueOf(3000.00));
        cashBoxRepository.save(box);

        TransactionRequest createReq = new TransactionRequest(
                TransactionType.EXPENSE,
                BigDecimal.valueOf(1000.00),
                "Overstated expense",
                LocalDate.now(),
                "Alice",
                testCategory.getId(),
                null,
                "Voc-upd-002",
                "Freestone Technologies LLP"
        );
        TransactionResponse created = transactionService.recordTransaction(createReq, "admin@example.com", null, null, null);
        assertThat(transactionService.getCashBoxDetails().balance()).isEqualByComparingTo("2000.00");

        // Edit: lower to 600 → balance should go 2000+1000-600 = 2400
        TransactionUpdateRequest updateReq = new TransactionUpdateRequest(
                BigDecimal.valueOf(600.00),
                "Corrected expense",
                LocalDate.now(),
                "Alice",
                testCategory.getId(),
                null,
                "Voc-upd-002",
                "Freestone Technologies LLP"
        );
        TransactionResponse updated = transactionService.updateTransaction(created.id(), updateReq);
        assertThat(updated.amount()).isEqualByComparingTo("600.00");
        assertThat(transactionService.getCashBoxDetails().balance()).isEqualByComparingTo("2400.00");
    }

    @Test
    @DisplayName("Updating topup amount adjusts cashbox balance correctly")
    @Transactional
    void updateTopupAmountAdjustsCashbox() throws Exception {
        // Record topup of 5000 → balance = 5000
        TransactionRequest createReq = new TransactionRequest(
                TransactionType.TOPUP,
                BigDecimal.valueOf(5000.00),
                "Initial topup",
                LocalDate.now(),
                "Bank",
                null,
                null,
                "Voc-upd-003",
                "Freestone Technologies LLP"
        );
        TransactionResponse created = transactionService.recordTransaction(createReq, "admin@example.com", null, null, null);
        assertThat(transactionService.getCashBoxDetails().balance()).isEqualByComparingTo("5000.00");

        // Edit: change to 7000 → balance should be 5000-5000+7000 = 7000
        TransactionUpdateRequest updateReq = new TransactionUpdateRequest(
                BigDecimal.valueOf(7000.00),
                "Corrected topup",
                LocalDate.now(),
                null,
                null,
                null,
                "Voc-upd-003",
                "Freestone Technologies LLP"
        );
        transactionService.updateTransaction(created.id(), updateReq);
        assertThat(transactionService.getCashBoxDetails().balance()).isEqualByComparingTo("7000.00");
    }

    @Test
    @DisplayName("Updating topup downwards exceeding available balance throws InsufficientBalanceException")
    @Transactional
    void updateTopupDownwardsExceedingBalanceThrows() throws Exception {
        // Setup: cashbox balance is 5000 (after a 5000 topup)
        TransactionRequest createReq = new TransactionRequest(
                TransactionType.TOPUP,
                BigDecimal.valueOf(5000.00),
                "Topup",
                LocalDate.now(),
                "Bank",
                null,
                null,
                "Voc-upd-topdown",
                "Freestone Technologies LLP"
        );
        TransactionResponse created = transactionService.recordTransaction(createReq, "admin@example.com", null, null, null);

        // Record expense of 4000 → remaining balance is 1000
        TransactionRequest expenseReq = new TransactionRequest(
                TransactionType.EXPENSE,
                BigDecimal.valueOf(4000.00),
                "Expense",
                LocalDate.now(),
                "Vendor",
                testCategory.getId(),
                null,
                "Voc-upd-topdown-exp",
                "Freestone Technologies LLP"
        );
        transactionService.recordTransaction(expenseReq, "admin@example.com", null, null, null);
        assertThat(transactionService.getCashBoxDetails().balance()).isEqualByComparingTo("1000.00");

        // Attempt to update the 5000 topup to 2000 (reduction is 3000, which exceeds remaining 1000 balance)
        TransactionUpdateRequest updateReq = new TransactionUpdateRequest(
                BigDecimal.valueOf(2000.00),
                "Corrected topup",
                LocalDate.now(),
                null,
                null,
                null,
                "Voc-upd-topdown",
                "Freestone Technologies LLP"
        );

        assertThatThrownBy(() -> transactionService.updateTransaction(created.id(), updateReq))
                .isInstanceOf(InsufficientBalanceException.class)
                .hasMessageContaining("Insufficient balance");
    }

    @Test
    @DisplayName("Updating expense with unchanged amount should NOT alter cashbox balance")
    @Transactional
    void updateWithUnchangedAmountDoesNotAlterBalance() throws Exception {
        // Setup: cashbox 2000, record 300 expense → balance 1700
        CashBox box = cashBoxRepository.findById(1L).orElseThrow();
        box.setBalance(BigDecimal.valueOf(2000.00));
        cashBoxRepository.save(box);

        TransactionRequest createReq = new TransactionRequest(
                TransactionType.EXPENSE,
                BigDecimal.valueOf(300.00),
                "Fixed expense",
                LocalDate.now(),
                "Bob",
                testCategory.getId(),
                null,
                "Voc-upd-004",
                "Freestone Technologies LLP"
        );
        TransactionResponse created = transactionService.recordTransaction(createReq, "admin@example.com", null, null, null);
        assertThat(transactionService.getCashBoxDetails().balance()).isEqualByComparingTo("1700.00");

        // Edit only description — amount stays 300
        TransactionUpdateRequest updateReq = new TransactionUpdateRequest(
                BigDecimal.valueOf(300.00),
                "Description corrected only",
                LocalDate.now(),
                "Bob",
                testCategory.getId(),
                null,
                "Voc-upd-004",
                "Freestone Technologies LLP"
        );
        TransactionResponse updated = transactionService.updateTransaction(created.id(), updateReq);
        assertThat(updated.description()).isEqualTo("Description corrected only");
        // Balance must remain unchanged
        assertThat(transactionService.getCashBoxDetails().balance()).isEqualByComparingTo("1700.00");
    }

    @Test
    @DisplayName("Updating expense to amount exceeding available cashbox balance throws InsufficientBalanceException")
    @Transactional
    void updateExpenseExceedingBalanceThrows() throws Exception {
        // Setup: cashbox 500, record 400 expense → balance 100
        CashBox box = cashBoxRepository.findById(1L).orElseThrow();
        box.setBalance(BigDecimal.valueOf(500.00));
        cashBoxRepository.save(box);

        TransactionRequest createReq = new TransactionRequest(
                TransactionType.EXPENSE,
                BigDecimal.valueOf(400.00),
                "Near-limit expense",
                LocalDate.now(),
                "Carol",
                testCategory.getId(),
                null,
                "Voc-upd-005",
                "Freestone Technologies LLP"
        );
        TransactionResponse created = transactionService.recordTransaction(createReq, "admin@example.com", null, null, null);
        assertThat(transactionService.getCashBoxDetails().balance()).isEqualByComparingTo("100.00");

        // Try to edit amount to 9999 — way over available balance (100+400=500 restored, but 9999>500)
        TransactionUpdateRequest updateReq = new TransactionUpdateRequest(
                BigDecimal.valueOf(9999.00),
                "Near-limit expense",
                LocalDate.now(),
                "Carol",
                testCategory.getId(),
                null,
                "Voc-upd-005",
                "Freestone Technologies LLP"
        );
        assertThatThrownBy(() -> transactionService.updateTransaction(created.id(), updateReq))
                .isInstanceOf(InsufficientBalanceException.class)
                .hasMessageContaining("Insufficient balance in Cash Box");
    }

    @Test
    @DisplayName("Editing transaction within configurable limit (1 month + 3 days) succeeds")
    @Transactional
    void editTransactionWithinTimeLimitSucceeds() throws Exception {
        // Setup cashbox
        CashBox box = cashBoxRepository.findById(1L).orElseThrow();
        box.setBalance(BigDecimal.valueOf(1000.00));
        cashBoxRepository.save(box);

        // Setup transaction recorded 15 days ago
        TransactionRequest createReq = new TransactionRequest(
                TransactionType.EXPENSE,
                BigDecimal.valueOf(100.00),
                "Recent transaction",
                LocalDate.now(),
                "John",
                testCategory.getId(),
                null,
                "Voc-time-001",
                "Freestone Technologies LLP"
        );
        TransactionResponse created = transactionService.recordTransaction(createReq, "admin@example.com", null, null, null);
        
        // Use JDBC to update the database record to 15 days ago
        java.time.Instant fifteenDaysAgo = java.time.Instant.now().minus(java.time.Duration.ofDays(15));
        jdbcTemplate.update("UPDATE transactions SET created_at = ? WHERE id = ?", fifteenDaysAgo, created.id());

        // Clear persistence context to force reload from DB
        entityManager.clear();

        // Perform edit
        TransactionUpdateRequest updateReq = new TransactionUpdateRequest(
                BigDecimal.valueOf(100.00),
                "Recent transaction - Edited",
                LocalDate.now(),
                "John",
                testCategory.getId(),
                null,
                "Voc-time-001",
                "Freestone Technologies LLP"
        );
        TransactionResponse updated = transactionService.updateTransaction(created.id(), updateReq);
        assertThat(updated.description()).isEqualTo("Recent transaction - Edited");
    }

    @Test
    @DisplayName("Editing transaction outside configurable limit (1 month + 3 days) throws IllegalArgumentException")
    @Transactional
    void editTransactionOutsideTimeLimitThrows() throws Exception {
        // Setup cashbox
        CashBox box = cashBoxRepository.findById(1L).orElseThrow();
        box.setBalance(BigDecimal.valueOf(1000.00));
        cashBoxRepository.save(box);

        // Setup transaction recorded 40 days ago
        TransactionRequest createReq = new TransactionRequest(
                TransactionType.EXPENSE,
                BigDecimal.valueOf(100.00),
                "Old transaction",
                LocalDate.now(),
                "John",
                testCategory.getId(),
                null,
                "Voc-time-002",
                "Freestone Technologies LLP"
        );
        TransactionResponse created = transactionService.recordTransaction(createReq, "admin@example.com", null, null, null);
        
        // Use JDBC to update the database record to 60 days ago (more than 1 month + 15 days)
        java.time.Instant sixtyDaysAgo = java.time.Instant.now().minus(java.time.Duration.ofDays(60));
        jdbcTemplate.update("UPDATE transactions SET created_at = ? WHERE id = ?", sixtyDaysAgo, created.id());

        // Clear persistence context to force reload from DB
        entityManager.clear();

        // Attempt edit — should throw IllegalArgumentException
        TransactionUpdateRequest updateReq = new TransactionUpdateRequest(
                BigDecimal.valueOf(100.00),
                "Old transaction - Attempted Edit",
                LocalDate.now(),
                "John",
                testCategory.getId(),
                null,
                "Voc-time-002",
                "Freestone Technologies LLP"
        );

        assertThatThrownBy(() -> transactionService.updateTransaction(created.id(), updateReq))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Transaction cannot be edited after");
    }

    @Test
    @DisplayName("exportVouchersZip with matching transactions generates a valid zip archive")
    @Transactional
    void exportVouchersZipGeneratesValidZip() throws Exception {
        // Setup cashbox
        CashBox box = cashBoxRepository.findById(1L).orElseThrow();
        box.setBalance(BigDecimal.valueOf(5000.00));
        cashBoxRepository.save(box);

        LocalDate testDate = LocalDate.now().plusYears(1);
        TransactionRequest createReq1 = new TransactionRequest(
                TransactionType.EXPENSE,
                BigDecimal.valueOf(100.00),
                "Expense 1",
                testDate,
                "Vendor A",
                testCategory.getId(),
                null,
                "Voc-bulk-001",
                "Freestone Technologies LLP"
        );
        TransactionResponse tx1 = transactionService.recordTransaction(createReq1, "admin@example.com", null, null, null);

        TransactionRequest createReq2 = new TransactionRequest(
                TransactionType.EXPENSE,
                BigDecimal.valueOf(200.00),
                "Expense 2",
                testDate,
                "Vendor B",
                testCategory.getId(),
                null,
                "Voc-bulk-002",
                "Freestone Technologies LLP"
        );
        TransactionResponse tx2 = transactionService.recordTransaction(createReq2, "admin@example.com", null, null, null);

        // Generate the ZIP
        byte[] zipBytes = transactionService.exportVouchersZip(testDate, testDate);
        assertThat(zipBytes).isNotEmpty();

        // Parse and assert inside the ZIP file structure
        java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(zipBytes));
        java.util.zip.ZipEntry entry;
        java.util.List<String> fileNames = new java.util.ArrayList<>();
        while ((entry = zis.getNextEntry()) != null) {
            fileNames.add(entry.getName());
            zis.closeEntry();
        }

        assertThat(fileNames).hasSize(2);
        assertThat(fileNames).containsExactlyInAnyOrder(
                "Voc-bulk-001-" + tx1.transactionNo() + ".pdf",
                "Voc-bulk-002-" + tx2.transactionNo() + ".pdf"
        );
    }

    @Test
    @DisplayName("exportVouchersZip with no matching transactions throws IllegalArgumentException")
    @Transactional
    void exportVouchersZipWithNoMatchThrows() {
        LocalDate farFuture = LocalDate.now().plusYears(10);
        assertThatThrownBy(() -> transactionService.exportVouchersZip(farFuture, farFuture))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No transactions with voucher numbers found for the selected date range");
    }

    @Test
    @DisplayName("getDashboardStats with custom date range returns filtered stats")
    @Transactional
    void getDashboardStatsWithCustomRangeFiltersCorrectly() throws Exception {
        // Setup cashbox
        CashBox box = cashBoxRepository.findById(1L).orElseThrow();
        box.setBalance(BigDecimal.valueOf(10000.00));
        cashBoxRepository.save(box);

        // Record a transaction today
        LocalDate today = LocalDate.now().plusYears(2);
        TransactionRequest createReq1 = new TransactionRequest(
                TransactionType.EXPENSE,
                BigDecimal.valueOf(100.00),
                "Expense Today",
                today,
                "Vendor A",
                testCategory.getId(),
                null,
                "Voc-stats-1",
                "Freestone Technologies LLP"
        );
        transactionService.recordTransaction(createReq1, "admin@example.com", null, null, null);

        // Record a transaction 5 months ago
        LocalDate pastDate = LocalDate.now().minusMonths(5);
        TransactionRequest createReq2 = new TransactionRequest(
                TransactionType.EXPENSE,
                BigDecimal.valueOf(500.00),
                "Expense Past",
                pastDate,
                "Vendor B",
                testCategory.getId(),
                null,
                "Voc-stats-2",
                "Freestone Technologies LLP"
        );
        transactionService.recordTransaction(createReq2, "admin@example.com", null, null, null);

        // Get stats for today only
        DashboardStatsResponse statsToday = transactionService.getDashboardStats(today, today);
        assertThat(statsToday.currentMonthSpent()).isEqualByComparingTo(BigDecimal.valueOf(100.00));
        assertThat(statsToday.currentMonthSpentCount()).isEqualTo(1L);

        // Get stats for the past date only
        DashboardStatsResponse statsPast = transactionService.getDashboardStats(pastDate, pastDate);
        assertThat(statsPast.currentMonthSpent()).isEqualByComparingTo(BigDecimal.valueOf(500.00));
        assertThat(statsPast.currentMonthSpentCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("getOrGenerateVoucher should generate voucher and set mock Google Drive ID")
    @Transactional
    void getOrGenerateVoucherUploadsToMockDrive() throws Exception {
        // Setup balance
        CashBox box = cashBoxRepository.findById(1L).orElseThrow();
        box.setBalance(BigDecimal.valueOf(5000.00));
        cashBoxRepository.save(box);

        // Record a transaction
        TransactionRequest request = new TransactionRequest(
                TransactionType.EXPENSE,
                BigDecimal.valueOf(100.00),
                "Test Expense",
                LocalDate.now(),
                "Vendor",
                testCategory.getId(),
                null,
                "Voc-drive-001",
                "Freestone Technologies LLP"
        );
        TransactionResponse response = transactionService.recordTransaction(request, "admin@example.com", null, null, null);

        // Generate voucher
        byte[] pdfBytes = transactionService.getOrGenerateVoucher(response.id());
        assertThat(pdfBytes).isNotEmpty();

        // Verify that the voucherFileId was saved on the transaction and contains "mock-file-id"
        PettyCashTransaction savedTx = entityManager.find(PettyCashTransaction.class, response.id());
        assertThat(savedTx.getVoucherFileId()).startsWith("mock-file-id-");
    }

    // ─── Optional Voucher Number & Enhanced Edit Tests ─────────────────────────

    @Test
    @DisplayName("Recording expense without voucher number succeeds")
    @Transactional
    void recordExpenseWithoutVoucherNumberSucceeds() throws Exception {
        CashBox box = cashBoxRepository.findById(1L).orElseThrow();
        box.setBalance(BigDecimal.valueOf(5000.00));
        cashBoxRepository.save(box);

        TransactionRequest request = new TransactionRequest(
                TransactionType.EXPENSE,
                BigDecimal.valueOf(300.00),
                "No voucher expense",
                LocalDate.now(),
                "Tony Stark",
                testCategory.getId(),
                testSubcategory.getId(),
                null, // No voucher number
                "Freestone Technologies LLP"
        );

        TransactionResponse response = transactionService.recordTransaction(request, "admin@example.com", null, null, null);

        assertThat(response.type()).isEqualTo(TransactionType.EXPENSE);
        assertThat(response.amount()).isEqualByComparingTo("300.00");
        assertThat(response.voucherNumber()).isNull();
        assertThat(transactionService.getCashBoxDetails().balance()).isEqualByComparingTo("4700.00");
    }

    @Test
    @DisplayName("Recording expense with voucher number succeeds")
    @Transactional
    void recordExpenseWithVoucherNumberSucceeds() throws Exception {
        CashBox box = cashBoxRepository.findById(1L).orElseThrow();
        box.setBalance(BigDecimal.valueOf(5000.00));
        cashBoxRepository.save(box);

        TransactionRequest request = new TransactionRequest(
                TransactionType.EXPENSE,
                BigDecimal.valueOf(200.00),
                "Voucher expense",
                LocalDate.now(),
                "Tony Stark",
                testCategory.getId(),
                null,
                "VOC-WITH-001",
                "Freestone Technologies LLP"
        );

        TransactionResponse response = transactionService.recordTransaction(request, "admin@example.com", null, null, null);

        assertThat(response.type()).isEqualTo(TransactionType.EXPENSE);
        assertThat(response.voucherNumber()).isEqualTo("VOC-WITH-001");
        assertThat(transactionService.getCashBoxDetails().balance()).isEqualByComparingTo("4800.00");
    }

    @Test
    @DisplayName("Updating transaction to add voucher number persists the change")
    @Transactional
    void updateTransactionVoucherNumber() throws Exception {
        CashBox box = cashBoxRepository.findById(1L).orElseThrow();
        box.setBalance(BigDecimal.valueOf(5000.00));
        cashBoxRepository.save(box);

        // Record without voucher
        TransactionRequest createReq = new TransactionRequest(
                TransactionType.EXPENSE,
                BigDecimal.valueOf(100.00),
                "Will add voucher later",
                LocalDate.now(),
                "John",
                testCategory.getId(),
                null,
                null,
                "Freestone Technologies LLP"
        );
        TransactionResponse created = transactionService.recordTransaction(createReq, "admin@example.com", null, null, null);
        assertThat(created.voucherNumber()).isNull();

        // Edit to add voucher number
        TransactionUpdateRequest updateReq = new TransactionUpdateRequest(
                BigDecimal.valueOf(100.00),
                "Will add voucher later",
                LocalDate.now(),
                "John",
                testCategory.getId(),
                null,
                "VOC-EDIT-001",
                "Freestone Technologies LLP"
        );
        TransactionResponse updated = transactionService.updateTransaction(created.id(), updateReq);
        assertThat(updated.voucherNumber()).isEqualTo("VOC-EDIT-001");
    }

    @Test
    @DisplayName("Updating transaction company name persists the change")
    @Transactional
    void updateTransactionCompanyName() throws Exception {
        CashBox box = cashBoxRepository.findById(1L).orElseThrow();
        box.setBalance(BigDecimal.valueOf(5000.00));
        cashBoxRepository.save(box);

        TransactionRequest createReq = new TransactionRequest(
                TransactionType.EXPENSE,
                BigDecimal.valueOf(100.00),
                "Company test",
                LocalDate.now(),
                "John",
                testCategory.getId(),
                null,
                "VOC-COMP-001",
                "Freestone Technologies LLP"
        );
        TransactionResponse created = transactionService.recordTransaction(createReq, "admin@example.com", null, null, null);
        assertThat(created.company()).isEqualTo("Freestone Technologies LLP");

        // Edit to change company
        TransactionUpdateRequest updateReq = new TransactionUpdateRequest(
                BigDecimal.valueOf(100.00),
                "Company test",
                LocalDate.now(),
                "John",
                testCategory.getId(),
                null,
                "VOC-COMP-001",
                "New Company Name"
        );
        TransactionResponse updated = transactionService.updateTransaction(created.id(), updateReq);
        assertThat(updated.company()).isEqualTo("New Company Name");
    }

    @Test
    @DisplayName("Downloading voucher is blocked when voucher number is not assigned")
    @Transactional
    void downloadVoucherBlockedWithoutVoucherNumber() throws Exception {
        CashBox box = cashBoxRepository.findById(1L).orElseThrow();
        box.setBalance(BigDecimal.valueOf(5000.00));
        cashBoxRepository.save(box);

        TransactionRequest request = new TransactionRequest(
                TransactionType.EXPENSE,
                BigDecimal.valueOf(100.00),
                "No voucher download test",
                LocalDate.now(),
                "John",
                testCategory.getId(),
                null,
                null,
                "Freestone Technologies LLP"
        );
        TransactionResponse response = transactionService.recordTransaction(request, "admin@example.com", null, null, null);

        assertThatThrownBy(() -> transactionService.getOrGenerateVoucher(response.id()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Voucher number must be assigned before downloading the voucher");
    }

    @Test
    @DisplayName("Downloading voucher succeeds after voucher number is assigned")
    @Transactional
    void downloadVoucherSucceedsWithVoucherNumber() throws Exception {
        CashBox box = cashBoxRepository.findById(1L).orElseThrow();
        box.setBalance(BigDecimal.valueOf(5000.00));
        cashBoxRepository.save(box);

        // Record without voucher
        TransactionRequest request = new TransactionRequest(
                TransactionType.EXPENSE,
                BigDecimal.valueOf(100.00),
                "Voucher download after edit",
                LocalDate.now(),
                "John",
                testCategory.getId(),
                null,
                null,
                "Freestone Technologies LLP"
        );
        TransactionResponse created = transactionService.recordTransaction(request, "admin@example.com", null, null, null);

        // Add voucher number via edit
        TransactionUpdateRequest updateReq = new TransactionUpdateRequest(
                BigDecimal.valueOf(100.00),
                "Voucher download after edit",
                LocalDate.now(),
                "John",
                testCategory.getId(),
                null,
                "VOC-DL-001",
                "Freestone Technologies LLP"
        );
        transactionService.updateTransaction(created.id(), updateReq);

        // Now download should succeed
        byte[] pdfBytes = transactionService.getOrGenerateVoucher(created.id());
        assertThat(pdfBytes).isNotEmpty();
    }

    @Test
    @DisplayName("Changing voucher number clears voucherFileId to force PDF regeneration")
    @Transactional
    void updateTransactionClearsVoucherFileIdOnVoucherNumberChange() throws Exception {
        CashBox box = cashBoxRepository.findById(1L).orElseThrow();
        box.setBalance(BigDecimal.valueOf(5000.00));
        cashBoxRepository.save(box);

        // Record with voucher
        TransactionRequest createReq = new TransactionRequest(
                TransactionType.EXPENSE,
                BigDecimal.valueOf(100.00),
                "Voucher regen test",
                LocalDate.now(),
                "John",
                testCategory.getId(),
                null,
                "VOC-REGEN-001",
                "Freestone Technologies LLP"
        );
        TransactionResponse created = transactionService.recordTransaction(createReq, "admin@example.com", null, null, null);

        // Generate voucher to set voucherFileId
        transactionService.getOrGenerateVoucher(created.id());

        PettyCashTransaction tx = entityManager.find(PettyCashTransaction.class, created.id());
        assertThat(tx.getVoucherFileId()).isNotNull();

        // Change voucher number — should clear the cached file ID
        TransactionUpdateRequest updateReq = new TransactionUpdateRequest(
                BigDecimal.valueOf(100.00),
                "Voucher regen test",
                LocalDate.now(),
                "John",
                testCategory.getId(),
                null,
                "VOC-REGEN-002",
                "Freestone Technologies LLP"
        );
        transactionService.updateTransaction(created.id(), updateReq);

        // Refresh from DB
        entityManager.flush();
        entityManager.clear();
        PettyCashTransaction updatedTx = entityManager.find(PettyCashTransaction.class, created.id());
        assertThat(updatedTx.getVoucherNumber()).isEqualTo("VOC-REGEN-002");
        assertThat(updatedTx.getVoucherFileId()).isNull();
    }

    @Test
    @DisplayName("Recording transaction with duplicate voucher number for same company and year throws exception")
    void duplicateVoucherNumberCompanyAndYearThrows() throws Exception {
        CashBox box = cashBoxRepository.findById(1L).orElseThrow();
        box.setBalance(BigDecimal.valueOf(10000.00));
        cashBoxRepository.save(box);
        LocalDate date = LocalDate.of(2026, 5, 10);
        TransactionRequest req1 = new TransactionRequest(
                TransactionType.EXPENSE,
                BigDecimal.valueOf(50.00),
                "Expense 1",
                date,
                "Recipient 1",
                testCategory.getId(),
                testSubcategory.getId(),
                "VOUCHER-001",
                "Freestone Infotech Pvt Ltd"
        );
        transactionService.recordTransaction(req1, "payer@example.com", null, null, null);

        // Same voucher, same company, same year -> should throw
        TransactionRequest req2 = new TransactionRequest(
                TransactionType.EXPENSE,
                BigDecimal.valueOf(30.00),
                "Expense 2",
                date,
                "Recipient 2",
                testCategory.getId(),
                testSubcategory.getId(),
                "VOUCHER-001",
                "Freestone Infotech Pvt Ltd"
        );
        assertThatThrownBy(() -> transactionService.recordTransaction(req2, "payer@example.com", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists for company 'Freestone Infotech Pvt Ltd' in year 2026");

        // Same voucher, DIFFERENT company -> succeeds
        TransactionRequest req3 = new TransactionRequest(
                TransactionType.EXPENSE,
                BigDecimal.valueOf(30.00),
                "Expense 3",
                date,
                "Recipient 3",
                testCategory.getId(),
                testSubcategory.getId(),
                "VOUCHER-001",
                "Freestone Technologies LLP"
        );
        org.assertj.core.api.Assertions.assertThatCode(() -> transactionService.recordTransaction(req3, "payer@example.com", null, null, null))
                .doesNotThrowAnyException();

        // Same voucher, same company, DIFFERENT year (2027) -> succeeds
        TransactionRequest req4 = new TransactionRequest(
                TransactionType.EXPENSE,
                BigDecimal.valueOf(30.00),
                "Expense 4",
                date.plusYears(1),
                "Recipient 4",
                testCategory.getId(),
                testSubcategory.getId(),
                "VOUCHER-001",
                "Freestone Infotech Pvt Ltd"
        );
        org.assertj.core.api.Assertions.assertThatCode(() -> transactionService.recordTransaction(req4, "payer@example.com", null, null, null))
                .doesNotThrowAnyException();
    }
}

