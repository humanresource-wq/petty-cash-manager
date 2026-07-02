package com.freestone.pettycash.service;

import com.freestone.pettycash.dto.CashBoxResponse;
import com.freestone.pettycash.dto.TransactionRequest;
import com.freestone.pettycash.dto.TransactionResponse;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
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

    @Transactional
    public TransactionResponse recordTransaction(TransactionRequest request, String payerEmail, byte[] fileBytes, String filename, String mimeType) throws IOException {
        // 1. Lock Cash Box optimistically
        CashBox box = cashBoxRepository.findById(1L)
                .orElseThrow(() -> new IllegalStateException("Cash Box system is not initialized"));

        Category category = null;
        Subcategory subcategory = null;

        // 2. Perform business validation
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

        // 3. Generate transaction sequence number TX-YYYY-XXXX
        String transactionNo = generateTransactionNo();

        // 4. Create and save entity
        PettyCashTransaction transaction = new PettyCashTransaction(
                transactionNo,
                request.type(),
                request.amount(),
                request.description(),
                request.date(),
                payerEmail,
                request.payee(),
                category,
                subcategory
        );

        // Handle optional atomic receipt upload
        if (fileBytes != null && fileBytes.length > 0) {
            String safeFilename = filename != null && !filename.isBlank() ? filename : "receipt.bin";
            String safeMimeType = mimeType != null && !mimeType.isBlank() ? mimeType : "application/octet-stream";
            String fileId = googleDriveService.uploadFile(transactionNo, safeFilename, fileBytes, safeMimeType);
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

    private String generateTransactionNo() {
        String yearPrefix = "TX-" + LocalDate.now().getYear();
        long currentYearCount = transactionRepository.countByTransactionNoPrefix(yearPrefix);
        return String.format("%s-%04d", yearPrefix, currentYearCount + 1);
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
        String fileId = googleDriveService.uploadFile(transaction.getTransactionNo(), filename, fileBytes, mimeType);

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

        // If already uploaded and saved in Google Drive, download it
        if (transaction.getVoucherFileId() != null && !transaction.getVoucherFileId().isBlank()) {
            return googleDriveService.downloadFile(transaction.getVoucherFileId());
        }

        // Generate voucher PDF bytes
        byte[] pdfBytes = voucherService.generateTransactionVoucher(transaction);

        // Upload to Google Drive and save fileId in DB
        String filename = "voucher-" + transaction.getTransactionNo() + ".pdf";
        String fileId = googleDriveService.uploadFile(
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
}
