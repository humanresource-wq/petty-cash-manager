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

    /**
     * Fetches all transactions with category and subcategory eagerly joined.
     * Used in export (CSV/PDF) to avoid LazyInitializationException outside a session.
     */
    @Query("SELECT t FROM PettyCashTransaction t LEFT JOIN FETCH t.category LEFT JOIN FETCH t.subcategory ORDER BY t.date DESC, t.id DESC")
    List<PettyCashTransaction> findAllWithAssociations();

    @Query(value = "SELECT t FROM PettyCashTransaction t " +
           "LEFT JOIN FETCH t.category " +
           "LEFT JOIN FETCH t.subcategory " +
           "WHERE (:startDate IS NULL OR t.date >= :startDate) " +
           "AND (:endDate IS NULL OR t.date <= :endDate) " +
           "AND (:type IS NULL OR t.type = :type) " +
           "AND (:categoryName IS NULL OR LOWER(t.category.name) = LOWER(:categoryName)) " +
           "AND (:search IS NULL OR LOWER(t.description) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(t.payee) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(t.transactionNo) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(t.payer) LIKE LOWER(CONCAT('%', :search, '%')))",
           countQuery = "SELECT COUNT(t) FROM PettyCashTransaction t " +
           "WHERE (:startDate IS NULL OR t.date >= :startDate) " +
           "AND (:endDate IS NULL OR t.date <= :endDate) " +
           "AND (:type IS NULL OR t.type = :type) " +
           "AND (:categoryName IS NULL OR LOWER(t.category.name) = LOWER(:categoryName)) " +
           "AND (:search IS NULL OR LOWER(t.description) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(t.payee) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(t.transactionNo) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(t.payer) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<PettyCashTransaction> findFilteredPaginated(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("type") TransactionType type,
            @Param("categoryName") String categoryName,
            @Param("search") String search,
            Pageable pageable
    );
}
