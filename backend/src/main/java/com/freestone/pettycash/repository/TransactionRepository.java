package com.freestone.pettycash.repository;

import com.freestone.pettycash.model.PettyCashTransaction;
import com.freestone.pettycash.model.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<PettyCashTransaction, Long> {

    Optional<PettyCashTransaction> findByTransactionNo(String transactionNo);

    List<PettyCashTransaction> findAllByOrderByDateDescIdDesc();

    List<PettyCashTransaction> findByPayerIgnoreCaseOrderByDateDesc(String payer);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM PettyCashTransaction t WHERE t.type = :type AND t.date BETWEEN :start AND :end")
    BigDecimal sumAmountByTypeAndDateRange(
            @Param("type") TransactionType type,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end
    );

    @Query("SELECT COUNT(t) FROM PettyCashTransaction t WHERE t.transactionNo LIKE :prefix%")
    long countByTransactionNoPrefix(@Param("prefix") String prefix);

    boolean existsByVoucherNumberAndDate(String voucherNumber, LocalDate date);

    @Query("SELECT COUNT(t) > 0 FROM PettyCashTransaction t " +
           "WHERE LOWER(t.voucherNumber) = LOWER(:voucherNumber) " +
           "AND LOWER(t.company) = LOWER(:company) " +
           "AND t.date BETWEEN :startOfYear AND :endOfYear " +
           "AND (:excludeId IS NULL OR t.id <> :excludeId)")
    boolean existsByVoucherNumberCompanyAndYear(
            @Param("voucherNumber") String voucherNumber,
            @Param("company") String company,
            @Param("startOfYear") LocalDate startOfYear,
            @Param("endOfYear") LocalDate endOfYear,
            @Param("excludeId") Long excludeId
    );

    /**
     * Fetches all transactions with category and subcategory eagerly joined.
     * Used in export (CSV/PDF) to avoid LazyInitializationException outside a session.
     */
    @Query("SELECT t FROM PettyCashTransaction t LEFT JOIN FETCH t.category LEFT JOIN FETCH t.subcategory ORDER BY t.date DESC, t.id DESC")
    List<PettyCashTransaction> findAllWithAssociations();

    @Query(value = "SELECT t FROM PettyCashTransaction t " +
           "LEFT JOIN FETCH t.category c " +
           "LEFT JOIN FETCH t.subcategory " +
           "WHERE (:hasStartDate = false OR t.date >= :startDate) " +
           "AND (:hasEndDate = false OR t.date <= :endDate) " +
           "AND (:hasType = false OR t.type = :type) " +
           "AND (:hasCategoryName = false OR LOWER(c.name) = :categoryName) " +
           "AND (:hasSearch = false OR LOWER(t.description) LIKE :search " +
           "OR LOWER(t.payee) LIKE :search " +
           "OR LOWER(t.transactionNo) LIKE :search " +
           "OR LOWER(t.payer) LIKE :search)",
           countQuery = "SELECT COUNT(t) FROM PettyCashTransaction t " +
           "LEFT JOIN t.category c " +
           "WHERE (:hasStartDate = false OR t.date >= :startDate) " +
           "AND (:hasEndDate = false OR t.date <= :endDate) " +
           "AND (:hasType = false OR t.type = :type) " +
           "AND (:hasCategoryName = false OR LOWER(c.name) = :categoryName) " +
           "AND (:hasSearch = false OR LOWER(t.description) LIKE :search " +
           "OR LOWER(t.payee) LIKE :search " +
           "OR LOWER(t.transactionNo) LIKE :search " +
           "OR LOWER(t.payer) LIKE :search)")
    Page<PettyCashTransaction> findFilteredPaginated(
            @Param("startDate") LocalDate startDate,
            @Param("hasStartDate") boolean hasStartDate,
            @Param("endDate") LocalDate endDate,
            @Param("hasEndDate") boolean hasEndDate,
            @Param("type") TransactionType type,
            @Param("hasType") boolean hasType,
            @Param("categoryName") String categoryName,
            @Param("hasCategoryName") boolean hasCategoryName,
            @Param("search") String search,
            @Param("hasSearch") boolean hasSearch,
            Pageable pageable
    );

    @Query("SELECT t FROM PettyCashTransaction t " +
           "LEFT JOIN FETCH t.category c " +
           "LEFT JOIN FETCH t.subcategory " +
           "WHERE (:hasStartDate = false OR t.date >= :startDate) " +
           "AND (:hasEndDate = false OR t.date <= :endDate) " +
           "AND (:hasCompany = false OR t.company = :company) " +
           "AND (:hasCategoryName = false OR LOWER(c.name) = LOWER(:categoryName)) " +
           "AND (:hasType = false OR t.type = :type) " +
           "AND (:hasSearch = false OR LOWER(t.description) LIKE :search " +
           "OR LOWER(t.payee) LIKE :search " +
           "OR LOWER(t.transactionNo) LIKE :search " +
           "OR LOWER(t.payer) LIKE :search) " +
           "ORDER BY t.date DESC, t.id DESC")
    List<PettyCashTransaction> findFilteredList(
            @Param("startDate") LocalDate startDate,
            @Param("hasStartDate") boolean hasStartDate,
            @Param("endDate") LocalDate endDate,
            @Param("hasEndDate") boolean hasEndDate,
            @Param("company") String company,
            @Param("hasCompany") boolean hasCompany,
            @Param("categoryName") String categoryName,
            @Param("hasCategoryName") boolean hasCategoryName,
            @Param("type") TransactionType type,
            @Param("hasType") boolean hasType,
            @Param("search") String search,
            @Param("hasSearch") boolean hasSearch
    );
}
