package com.freestone.pettycash.service;

import com.freestone.pettycash.dto.CashBoxResponse;
import com.freestone.pettycash.dto.DashboardStatsResponse;
import com.freestone.pettycash.dto.TransactionRequest;
import com.freestone.pettycash.dto.TransactionResponse;
import com.freestone.pettycash.dto.TransactionUpdateRequest;
import com.freestone.pettycash.exception.InsufficientBalanceException;
import com.freestone.pettycash.exception.ResourceNotFoundException;
import com.freestone.pettycash.mapper.TransactionMapper;
import com.freestone.pettycash.model.*;
import com.freestone.pettycash.repository.CashBoxRepository;
import com.freestone.pettycash.repository.CategoryRepository;
import com.freestone.pettycash.repository.SubcategoryRepository;
import com.freestone.pettycash.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import com.freestone.pettycash.config.AppProperties;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final CashBoxRepository cashBoxRepository;
    private final CategoryRepository categoryRepository;
    private final SubcategoryRepository subcategoryRepository;
    private final GoogleDriveService googleDriveService;
    private final VoucherService voucherService;
    private final TransactionMapper mapper;
    private final AppProperties appProperties;

    @Transactional
    public TransactionResponse recordTransaction(TransactionRequest request, String payerEmail, byte[] fileBytes, String filename, String mimeType) throws IOException {
        // 1. Lock Cash Box optimistically
        CashBox box = cashBoxRepository.findById(1L)
                .orElseThrow(() -> new IllegalStateException("Cash Box system is not initialized"));

        Category category = null;
        Subcategory subcategory = null;

        // 2. Perform business validation
        if (request.company() == null || request.company().trim().isBlank()) {
            throw new IllegalArgumentException("Company must not be blank");
        }

        // Resolve voucher number: optional for EXPENSE, auto-generated for TOPUP
        String resolvedVoucherNumber;
        if (request.type() == TransactionType.TOPUP) {
            resolvedVoucherNumber = (request.voucherNumber() != null && !request.voucherNumber().trim().isBlank())
                    ? request.voucherNumber().trim()
                    : "TOPUP-" + java.time.format.DateTimeFormatter
                            .ofPattern("yyyyMMdd-HHmmssSSS")
                            .format(java.time.LocalDateTime.now());
        } else {
            // Voucher number is optional for expenses — can be assigned later
            resolvedVoucherNumber = (request.voucherNumber() != null && !request.voucherNumber().trim().isBlank())
                    ? request.voucherNumber().trim()
                    : null;
        }

        if (resolvedVoucherNumber != null && transactionRepository.existsByVoucherNumberAndDate(resolvedVoucherNumber, request.date())) {
            throw new IllegalArgumentException("Voucher number '" + resolvedVoucherNumber + "' is already registered on " + request.date());
        }

        if (request.type() == TransactionType.EXPENSE) {
            if (box.getBalance().compareTo(request.amount()) < 0) {
                throw new InsufficientBalanceException(request.amount(), box.getBalance());
            }

            if (request.categoryId() == null) {
                throw new IllegalArgumentException("Category must be provided for expense transactions");
            }

            category = categoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category", "id", request.categoryId()));

            if (request.subcategoryId() != null) {
                subcategory = subcategoryRepository.findById(request.subcategoryId())
                        .orElseThrow(() -> new ResourceNotFoundException("Subcategory", "id", request.subcategoryId()));
                if (!subcategory.getCategory().getId().equals(category.getId())) {
                    throw new IllegalArgumentException("Subcategory does not belong to the selected Category");
                }
            }

            // Deduct from balance
            box.setBalance(box.getBalance().subtract(request.amount()));
        } else {
            // TOPUP: Add to balance
            box.setBalance(box.getBalance().add(request.amount()));
        }

        // Save box to register balance changes (increments version)
        cashBoxRepository.save(box);

        // 3. Create transaction with a temporary UUID to obtain the autoincrement ID
        String tempTxNo = java.util.UUID.randomUUID().toString();

        PettyCashTransaction transaction = new PettyCashTransaction(
                tempTxNo,
                request.type(),
                request.amount(),
                request.description(),
                request.date(),
                payerEmail,
                request.payee(),
                category,
                subcategory
        );
        transaction.setVoucherNumber(resolvedVoucherNumber);
        transaction.setCompany(request.company().trim());

        // Save first to get the database id
        transaction = transactionRepository.save(transaction);

        // 4. Generate sequential transaction no using the database ID
        String transactionNo = String.format("TX-%05d", transaction.getId());
        transaction.setTransactionNo(transactionNo);
        transaction = transactionRepository.save(transaction);

        // 5. Handle optional atomic receipt upload
        if (fileBytes != null && fileBytes.length > 0) {
            String safeFilename = filename != null && !filename.isBlank() ? filename : "receipt.bin";
            String safeMimeType = mimeType != null && !mimeType.isBlank() ? mimeType : "application/octet-stream";
            String categoryFolder = getCategoryFolderName(transaction);
            String fileId = googleDriveService.uploadFile(categoryFolder, transactionNo, safeFilename, fileBytes, safeMimeType);
            transaction.setReceiptFileId(fileId);
            transaction.setReceiptName(safeFilename);
            transaction.setReceiptStatus(ReceiptStatus.RECEIVED);
        } else {
            transaction.setReceiptStatus(request.type() == TransactionType.TOPUP ? ReceiptStatus.NA : ReceiptStatus.PENDING);
        }

        transaction = transactionRepository.save(transaction);
        log.info("Successfully recorded transaction {} (Type: {}, Amount: {})",
                transactionNo, request.type(), request.amount());

        return mapper.toResponse(transaction);
    }

    public List<TransactionResponse> listAllTransactions() {
        return transactionRepository.findAllByOrderByDateDescIdDesc().stream()
                .map(mapper::toResponse)
                .toList();
    }

    public List<TransactionResponse> listUserTransactions(String email) {
        return transactionRepository.findByPayerIgnoreCaseOrderByDateDesc(email).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional
    public TransactionResponse uploadReceipt(Long transactionId, String filename, byte[] fileBytes, String mimeType) throws IOException {
        PettyCashTransaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", transactionId));

        if (transaction.getType() == TransactionType.TOPUP) {
            throw new IllegalArgumentException("Receipt uploads are not allowed for Top-ups");
        }

        // Upload to Google Drive folder for this transaction
        String categoryFolder = getCategoryFolderName(transaction);
        String fileId = googleDriveService.uploadFile(categoryFolder, transaction.getTransactionNo(), filename, fileBytes, mimeType);

        // Update database
        transaction.setReceiptFileId(fileId);
        transaction.setReceiptName(filename);
        transaction.setReceiptStatus(ReceiptStatus.RECEIVED);

        transaction = transactionRepository.save(transaction);
        log.info("Uploaded receipt (id: {}) for transaction {}", fileId, transaction.getTransactionNo());

        return mapper.toResponse(transaction);
    }

    public byte[] downloadReceipt(Long transactionId) throws IOException {
        PettyCashTransaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", transactionId));

        if (transaction.getReceiptFileId() == null || transaction.getReceiptFileId().isBlank()) {
            throw new IllegalArgumentException("Transaction does not have an attached receipt");
        }

        return googleDriveService.downloadFile(transaction.getReceiptFileId());
    }

    @Transactional
    public byte[] getOrGenerateVoucher(Long transactionId) throws IOException {
        PettyCashTransaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", transactionId));

        // Block voucher generation if voucher number is not assigned
        if (transaction.getVoucherNumber() == null || transaction.getVoucherNumber().isBlank()) {
            throw new IllegalArgumentException("Voucher number must be assigned before downloading the voucher.");
        }

        // If already uploaded and saved in Google Drive, download it
        if (transaction.getVoucherFileId() != null && !transaction.getVoucherFileId().isBlank()) {
            if (!transaction.getVoucherFileId().startsWith("mock-file-id-")) {
                return googleDriveService.downloadFile(transaction.getVoucherFileId());
            }
        }

        // Generate voucher PDF bytes
        byte[] pdfBytes = voucherService.generateTransactionVoucher(transaction);

        // Build category-month folder name for Google Drive (Option B hierarchy)
        String categoryFolder = getCategoryFolderName(transaction);

        // Upload to Google Drive and save fileId in DB
        // Build filename: sanitize voucher number for safe filesystem use, then append TX no
        String safeVoucherNo = transaction.getVoucherNumber().replaceAll("[^a-zA-Z0-9\\-_]", "_");
        String filename = safeVoucherNo + "-" + transaction.getTransactionNo() + ".pdf";
        String fileId = googleDriveService.uploadFile(
                categoryFolder,
                transaction.getTransactionNo(),
                filename,
                pdfBytes,
                "application/pdf"
        );

        transaction.setVoucherFileId(fileId);
        transactionRepository.save(transaction);
        log.info("Generated and saved PDF voucher (id: {}) for transaction {}", fileId, transaction.getTransactionNo());

        return pdfBytes;
    }

    /**
     * Returns the download filename for a voucher in the format:
     * sanitizedVoucherNumber-transactionNo.pdf
     */
    public String getVoucherFilename(Long transactionId) {
        PettyCashTransaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", transactionId));
        String safeVoucherNo = transaction.getVoucherNumber().replaceAll("[^a-zA-Z0-9\\-_]", "_");
        return safeVoucherNo + "-" + transaction.getTransactionNo() + ".pdf";
    }

    @Transactional
    public TransactionResponse toggleReceiptReceived(Long transactionId, ReceiptStatus status) {
        PettyCashTransaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", transactionId));

        if (transaction.getType() == TransactionType.TOPUP) {
            throw new IllegalArgumentException("Receipt status cannot be modified on Top-ups");
        }

        transaction.setReceiptStatus(status);
        return mapper.toResponse(transactionRepository.save(transaction));
    }

    @Transactional
    public TransactionResponse updateTransaction(Long id, TransactionUpdateRequest request) {
        PettyCashTransaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", id));

        if (transaction.getCreatedAt() != null) {
            java.time.Instant limit = transaction.getCreatedAt()
                    .atZone(java.time.ZoneOffset.UTC)
                    .plusMonths(appProperties.getTransaction().getEditLimit().getMonths())
                    .plusDays(appProperties.getTransaction().getEditLimit().getDays())
                    .toInstant();
            if (java.time.Instant.now().isAfter(limit)) {
                throw new IllegalArgumentException("Transaction cannot be edited after " +
                        appProperties.getTransaction().getEditLimit().getMonths() + " month(s) and " +
                        appProperties.getTransaction().getEditLimit().getDays() + " day(s) from recording.");
            }
        }

        CashBox box = cashBoxRepository.findById(1L)
                .orElseThrow(() -> new IllegalStateException("Cash Box system is not initialized"));

        BigDecimal oldAmount = transaction.getAmount();
        BigDecimal newAmount = request.amount();

        // Adjust cashbox balance only if amount changed
        if (oldAmount.compareTo(newAmount) != 0) {
            if (transaction.getType() == TransactionType.EXPENSE) {
                // Reverse old deduction, apply new deduction
                BigDecimal restoredBalance = box.getBalance().add(oldAmount);
                if (restoredBalance.compareTo(newAmount) < 0) {
                    throw new InsufficientBalanceException(newAmount, restoredBalance);
                }
                box.setBalance(restoredBalance.subtract(newAmount));
            } else {
                // TOPUP: reverse old credit, apply new credit
                BigDecimal adjustedBalance = box.getBalance().subtract(oldAmount).add(newAmount);
                if (adjustedBalance.compareTo(BigDecimal.ZERO) < 0) {
                    throw new InsufficientBalanceException(oldAmount.subtract(newAmount), box.getBalance());
                }
                box.setBalance(adjustedBalance);
            }
            cashBoxRepository.save(box);
        }

        // Update editable fields
        transaction.setAmount(newAmount);
        transaction.setDescription(request.description());
        transaction.setDate(request.date());
        transaction.setPayee(request.payee());
        transaction.setCompany(request.company().trim());

        // Handle voucher number update — clear cached voucher PDF if number changed
        String oldVoucherNumber = transaction.getVoucherNumber();
        String newVoucherNumber = (request.voucherNumber() != null && !request.voucherNumber().isBlank())
                ? request.voucherNumber().trim()
                : null;
        boolean voucherChanged = (oldVoucherNumber == null && newVoucherNumber != null)
                || (oldVoucherNumber != null && !oldVoucherNumber.equals(newVoucherNumber));

        if (newVoucherNumber != null && voucherChanged
                && transactionRepository.existsByVoucherNumberAndDate(newVoucherNumber, request.date())) {
            throw new IllegalArgumentException("Voucher number '" + newVoucherNumber + "' is already registered on " + request.date());
        }

        transaction.setVoucherNumber(newVoucherNumber);
        if (voucherChanged) {
            transaction.setVoucherFileId(null); // Force regeneration on next download
        }

        // Update category / subcategory (only for EXPENSE)
        if (transaction.getType() == TransactionType.EXPENSE) {
            if (request.categoryId() == null) {
                throw new IllegalArgumentException("Category must be provided for expense transactions");
            }
            Category category = categoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category", "id", request.categoryId()));
            transaction.setCategory(category);

            if (request.subcategoryId() != null) {
                Subcategory subcategory = subcategoryRepository.findById(request.subcategoryId())
                        .orElseThrow(() -> new ResourceNotFoundException("Subcategory", "id", request.subcategoryId()));
                if (!subcategory.getCategory().getId().equals(category.getId())) {
                    throw new IllegalArgumentException("Subcategory does not belong to the selected Category");
                }
                transaction.setSubcategory(subcategory);
            } else {
                transaction.setSubcategory(null);
            }
        }

        transaction = transactionRepository.save(transaction);
        log.info("Updated transaction {} (Type: {}, OldAmount: {}, NewAmount: {})",
                transaction.getTransactionNo(), transaction.getType(), oldAmount, newAmount);

        return mapper.toResponse(transaction);
    }

    @Transactional
    public TransactionResponse updateTransactionWithReceipt(Long id, TransactionUpdateRequest request,
                                                             byte[] fileBytes, String filename, String mimeType) throws IOException {
        // Delegate core update logic
        TransactionResponse response = updateTransaction(id, request);

        // Handle optional receipt file upload
        if (fileBytes != null && fileBytes.length > 0) {
            PettyCashTransaction transaction = transactionRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", id));

            String safeFilename = filename != null && !filename.isBlank() ? filename : "receipt.bin";
            String safeMimeType = mimeType != null && !mimeType.isBlank() ? mimeType : "application/octet-stream";
            String categoryFolder = getCategoryFolderName(transaction);
            String fileId = googleDriveService.uploadFile(categoryFolder, transaction.getTransactionNo(), safeFilename, fileBytes, safeMimeType);
            transaction.setReceiptFileId(fileId);
            transaction.setReceiptName(safeFilename);
            transaction.setReceiptStatus(ReceiptStatus.RECEIVED);

            transaction = transactionRepository.save(transaction);
            log.info("Updated receipt (id: {}) for transaction {}", fileId, transaction.getTransactionNo());
            return mapper.toResponse(transaction);
        }

        return response;
    }

    public CashBoxResponse getCashBoxDetails() {
        CashBox box = cashBoxRepository.findById(1L)
                .orElseThrow(() -> new IllegalStateException("Cash Box system is not initialized"));
        return new CashBoxResponse(box.getBalance(), box.getLowThreshold());
    }

    @Transactional
    public CashBoxResponse updateLowThreshold(BigDecimal threshold) {
        if (threshold.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Threshold cannot be negative");
        }
        CashBox box = cashBoxRepository.findById(1L)
                .orElseThrow(() -> new IllegalStateException("Cash Box system is not initialized"));
        box.setLowThreshold(threshold);
        return new CashBoxResponse(box.getBalance(), cashBoxRepository.save(box).getLowThreshold());
    }

    public DashboardStatsResponse getDashboardStats(LocalDate startDate, LocalDate endDate) {
        CashBox box = cashBoxRepository.findById(1L)
                .orElseThrow(() -> new IllegalStateException("Cash Box system is not initialized"));

        LocalDate today = LocalDate.now();
        LocalDate rangeStart = (startDate != null) ? startDate : LocalDate.of(1970, 1, 1);
        LocalDate rangeEnd = (endDate != null) ? endDate : LocalDate.now().plusYears(100);

        BigDecimal spentThisMonth = transactionRepository.sumAmountByTypeAndDateRange(
                TransactionType.EXPENSE, rangeStart, rangeEnd);
        BigDecimal addedThisMonth = transactionRepository.sumAmountByTypeAndDateRange(
                TransactionType.TOPUP, rangeStart, rangeEnd);

        List<PettyCashTransaction> allTransactions = transactionRepository.findAll();

        long currentMonthSpentCount = allTransactions.stream()
                .filter(t -> t.getType() == TransactionType.EXPENSE && !t.getDate().isBefore(rangeStart) && !t.getDate().isAfter(rangeEnd))
                .count();

        long pendingReceiptsCount = allTransactions.stream()
                .filter(t -> t.getType() == TransactionType.EXPENSE && t.getReceiptStatus() == ReceiptStatus.PENDING)
                .count();

        BigDecimal pendingReceiptsValue = allTransactions.stream()
                .filter(t -> t.getType() == TransactionType.EXPENSE && t.getReceiptStatus() == ReceiptStatus.PENDING)
                .map(PettyCashTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Trend data: dynamically calculate based on filters, or last 6 months by default
        java.util.List<DashboardStatsResponse.MonthlyFlow> monthlyFlows = new java.util.ArrayList<>();
        LocalDate trendStart = (startDate != null) ? startDate.withDayOfMonth(1) : today.minusMonths(5).withDayOfMonth(1);
        LocalDate trendEnd = (endDate != null) ? endDate : today;

        LocalDate cursor = trendStart;
        int count = 0;
        while (!cursor.isAfter(trendEnd) && count < 12) {
            LocalDate start = cursor.withDayOfMonth(1);
            LocalDate end = cursor.plusMonths(1).withDayOfMonth(1).minusDays(1);

            BigDecimal monthlySpent = transactionRepository.sumAmountByTypeAndDateRange(
                    TransactionType.EXPENSE, start, end);
            BigDecimal monthlyAdded = transactionRepository.sumAmountByTypeAndDateRange(
                    TransactionType.TOPUP, start, end);

            String label = cursor.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH)
                    + " " + (cursor.getYear() % 100);
            monthlyFlows.add(new DashboardStatsResponse.MonthlyFlow(label, monthlySpent, monthlyAdded));

            cursor = cursor.plusMonths(1);
            count++;
        }

        if (monthlyFlows.isEmpty()) {
            for (int i = 5; i >= 0; i--) {
                LocalDate targetMonth = today.minusMonths(i);
                LocalDate start = targetMonth.withDayOfMonth(1);
                LocalDate end = targetMonth.plusMonths(1).withDayOfMonth(1).minusDays(1);

                BigDecimal monthlySpent = transactionRepository.sumAmountByTypeAndDateRange(
                        TransactionType.EXPENSE, start, end);
                BigDecimal monthlyAdded = transactionRepository.sumAmountByTypeAndDateRange(
                        TransactionType.TOPUP, start, end);

                String label = targetMonth.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH);
                monthlyFlows.add(new DashboardStatsResponse.MonthlyFlow(label, monthlySpent, monthlyAdded));
            }
        }

        // Category Spends in range
        java.util.Map<String, BigDecimal> catSpendsMap = new java.util.HashMap<>();
        allTransactions.stream()
                .filter(t -> t.getType() == TransactionType.EXPENSE && t.getCategory() != null)
                .filter(t -> !t.getDate().isBefore(rangeStart) && !t.getDate().isAfter(rangeEnd))
                .forEach(t -> {
                    String catName = t.getCategory().getName();
                    catSpendsMap.put(catName, catSpendsMap.getOrDefault(catName, BigDecimal.ZERO).add(t.getAmount()));
                });

        java.util.List<DashboardStatsResponse.CategorySpend> categorySpends = catSpendsMap.entrySet().stream()
                .map(e -> new DashboardStatsResponse.CategorySpend(e.getKey(), e.getValue()))
                .sorted((a, b) -> b.value().compareTo(a.value()))
                .toList();

        return new DashboardStatsResponse(
                box.getBalance(),
                box.getLowThreshold(),
                spentThisMonth,
                currentMonthSpentCount,
                addedThisMonth,
                pendingReceiptsCount,
                pendingReceiptsValue,
                monthlyFlows,
                categorySpends
        );
    }

    public List<PettyCashTransaction> getFilteredTransactions(
            LocalDate startDate,
            LocalDate endDate,
            TransactionType type,
            String categoryName,
            ReceiptStatus receiptStatus,
            String search
    ) {
        List<PettyCashTransaction> list = transactionRepository.findAllWithAssociations();

        return list.stream()
                .filter(t -> startDate == null || !t.getDate().isBefore(startDate))
                .filter(t -> endDate == null || !t.getDate().isAfter(endDate))
                .filter(t -> type == null || t.getType() == type)
                .filter(t -> categoryName == null || categoryName.isBlank() ||
                        (t.getCategory() != null && t.getCategory().getName().equalsIgnoreCase(categoryName)))
                .filter(t -> receiptStatus == null || t.getReceiptStatus() == receiptStatus)
                .filter(t -> {
                    if (search == null || search.isBlank()) {
                        return true;
                    }
                    String q = search.toLowerCase();
                    boolean descMatch = t.getDescription() != null && t.getDescription().toLowerCase().contains(q);
                    boolean payeeMatch = t.getPayee() != null && t.getPayee().toLowerCase().contains(q);
                    boolean txNoMatch = t.getTransactionNo() != null && t.getTransactionNo().toLowerCase().contains(q);
                    boolean payerMatch = t.getPayer() != null && t.getPayer().toLowerCase().contains(q);
                    return descMatch || payeeMatch || txNoMatch || payerMatch;
                })
                .toList();
    }

    public byte[] exportVouchersZip(LocalDate startDate, LocalDate endDate) throws IOException {
        List<PettyCashTransaction> allList = getFilteredTransactions(startDate, endDate, null, null, null, null);

        // Filter to only include transactions that have a voucher number assigned
        List<PettyCashTransaction> list = allList.stream()
                .filter(tx -> tx.getVoucherNumber() != null && !tx.getVoucherNumber().isBlank())
                .toList();

        if (list.isEmpty()) {
            throw new IllegalArgumentException("No transactions with voucher numbers found for the selected date range");
        }

        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(baos)) {
            for (PettyCashTransaction tx : list) {
                byte[] pdfBytes = voucherService.generateTransactionVoucher(tx);
                String safeVoucherNo = tx.getVoucherNumber().replaceAll("[^a-zA-Z0-9\\-_]", "_");
                java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry(safeVoucherNo + "-" + tx.getTransactionNo() + ".pdf");
                zos.putNextEntry(entry);
                zos.write(pdfBytes);
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }

    public Page<TransactionResponse> getPaginatedTransactions(
            int page,
            int size,
            LocalDate startDate,
            LocalDate endDate,
            TransactionType type,
            String categoryName,
            String search,
            String sortBy,
            String sortDir
    ) {
        log.info("getPaginatedTransactions: page={}, size={}, startDate={}, endDate={}, type={}, categoryName={}, search='{}', sortBy={}, sortDir={}",
                page, size, startDate, endDate, type, categoryName, search != null ? search : "", sortBy, sortDir);

        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String sortProperty = "date".equalsIgnoreCase(sortBy) ? "date" : "timestamp";
        
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortProperty, "id"));
        
        String searchParam = (search == null || search.isBlank()) ? null : "%" + search.trim().toLowerCase() + "%";
        String categoryParam = (categoryName == null || categoryName.isBlank()) ? null : categoryName.trim().toLowerCase();

        Page<PettyCashTransaction> entityPage = transactionRepository.findFilteredPaginated(
                startDate,
                startDate != null,
                endDate,
                endDate != null,
                type,
                type != null,
                categoryParam,
                categoryParam != null,
                searchParam,
                searchParam != null,
                pageable);

        log.info("getPaginatedTransactions response: totalElements={}, totalPages={}, numberOfElements={}",
                entityPage.getTotalElements(), entityPage.getTotalPages(), entityPage.getNumberOfElements());

        return entityPage.map(mapper::toResponse);
    }

    private String getCategoryFolderName(PettyCashTransaction transaction) {
        String categoryName = (transaction.getCategory() != null)
                ? transaction.getCategory().getName()
                : (transaction.getType() == TransactionType.TOPUP ? "Top-up" : "Uncategorized");
        String monthYear = java.time.format.DateTimeFormatter
                .ofPattern("MMM yyyy")
                .format(transaction.getDate());
        return categoryName + " - " + monthYear;
    }
}
