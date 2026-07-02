---
name: jpa
description: Guide for JPA entity design, Spring Data repositories, relationships, custom queries, and transaction management. Use when creating or modifying database entities, writing queries, or managing transactions in the Petty Cash Manager backend.
---

# jpa

Best practices for JPA entities, repositories, relationships, queries, and transaction management in the Petty Cash Manager application.

## When to run

Invoke this skill whenever:
- Creating or modifying entity classes in `backend/src/main/java/**/model/`.
- Adding or modifying repository interfaces in `backend/src/main/java/**/repository/`.
- Writing JPQL or native queries.
- Designing entity relationships (OneToMany, ManyToOne, etc.).
- Managing transactional boundaries.

## Workflow

### Step 1 — Entity Design

Follow these conventions for all JPA entities:

```java
@Entity
@Table(name = "transactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA requires no-arg constructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private LocalDate date;

    private String category;

    private String paidBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReceiptStatus receiptStatus = ReceiptStatus.PENDING;

    private String receiptNo;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
```

**Rules:**
1. Always use `@Table(name = "...")` with explicit table names (lowercase, plural, snake_case).
2. Use `@Enumerated(EnumType.STRING)` — never `ORDINAL` (breaks if enum order changes).
3. Use `BigDecimal` with `precision` and `scale` for monetary columns.
4. Use `LocalDate` / `LocalDateTime` — never `java.util.Date`.
5. Add `@PrePersist` / `@PreUpdate` for audit timestamps.
6. Use Lombok `@Getter` but **not** `@Setter` on entities — control mutations via methods.
7. Provide a `protected` no-arg constructor for JPA, and a builder or factory for application code.

### Step 2 — Repository Design

Extend `JpaRepository` and add custom query methods:

```java
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByTypeOrderByDateDesc(TransactionType type);

    List<Transaction> findByDateBetweenOrderByDateDesc(LocalDate start, LocalDate end);

    @Query("SELECT t FROM Transaction t WHERE t.date >= :start AND t.date <= :end AND t.type = :type")
    List<Transaction> findByDateRangeAndType(
        @Param("start") LocalDate start,
        @Param("end") LocalDate end,
        @Param("type") TransactionType type
    );

    @Query("SELECT COALESCE(SUM(CASE WHEN t.type = 'TOPUP' THEN t.amount ELSE -t.amount END), 0) FROM Transaction t")
    BigDecimal calculateBalance();

    @Query("SELECT t.category, SUM(t.amount) FROM Transaction t WHERE t.type = 'EXPENSE' AND t.date >= :start GROUP BY t.category ORDER BY SUM(t.amount) DESC")
    List<Object[]> expensesByCategory(@Param("start") LocalDate start);

    long countByReceiptStatus(ReceiptStatus status);
}
```

**Rules:**
1. Prefer Spring Data derived query methods for simple lookups.
2. Use `@Query` with JPQL for complex queries — only use native queries when JPQL is insufficient.
3. Always use `@Param` for named parameters in `@Query`.
4. Return `Optional<T>` for single-entity lookups, `List<T>` for collections.

### Step 3 — Relationships

When the schema evolves to include related entities (e.g., `Category`, `Member`, `Fund`):

```java
// Category entity
@Entity
@Table(name = "categories")
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @OneToMany(mappedBy = "category", fetch = FetchType.LAZY)
    private List<Transaction> transactions = new ArrayList<>();
}

// Transaction with relationship
@Entity
@Table(name = "transactions")
public class Transaction {
    // ...

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;
}
```

**Rules:**
1. Always set `fetch = FetchType.LAZY` — never `EAGER`.
2. Define `mappedBy` on the non-owning side of bidirectional relationships.
3. Use `@JoinColumn` on the owning side to control the FK column name.
4. Initialize collection fields (e.g., `new ArrayList<>()`) to avoid `NullPointerException`.
5. For N+1 prevention, use `@EntityGraph` or `JOIN FETCH` in queries.

### Step 4 — Transaction Management

```java
@Service
@Transactional(readOnly = true) // Default: read-only for query methods
@RequiredArgsConstructor
public class TransactionService {

    @Transactional // Writable transaction for mutations
    public TransactionResponse recordExpense(TransactionRequest request) {
        BigDecimal currentBalance = transactionRepository.calculateBalance();

        if (currentBalance.compareTo(request.amount()) < 0) {
            throw new InsufficientBalanceException(request.amount(), currentBalance);
        }

        Transaction entity = mapper.toEntity(request);
        return mapper.toResponse(transactionRepository.save(entity));
    }
}
```

**Rules:**
1. Class-level `@Transactional(readOnly = true)` for optimized reads.
2. Method-level `@Transactional` for write operations — overrides class-level.
3. Never call `@Transactional` methods from within the same class (proxy bypass issue).
4. Keep transactions short — avoid long-running operations within a transactional boundary.

### Step 5 — Database Migrations

Use Flyway for schema versioning:

```
backend/src/main/resources/db/migration/
├── V1__create_transactions_table.sql
├── V2__create_categories_table.sql
└── V3__add_receipt_columns.sql
```

```sql
-- V1__create_transactions_table.sql
CREATE TABLE transactions (
    id          BIGSERIAL PRIMARY KEY,
    type        VARCHAR(10) NOT NULL,
    amount      NUMERIC(12, 2) NOT NULL,
    description VARCHAR(255) NOT NULL,
    date        DATE NOT NULL,
    category    VARCHAR(100),
    paid_by     VARCHAR(100),
    receipt_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    receipt_no  VARCHAR(50),
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP
);
```

**Rules:**
1. Never modify existing migration files — always create new versioned scripts.
2. Set `spring.jpa.hibernate.ddl-auto=validate` to ensure entities match migrations.
3. Test migrations against an empty database to verify clean startup.

### Step 6 — Verify

After any entity or repository changes:
```bash
# Run tests
./mvnw test -pl backend

# Verify migration and entity sync
./mvnw spring-boot:run -pl backend -Dspring-boot.run.profiles=dev
# Check logs for Hibernate validation errors
```
