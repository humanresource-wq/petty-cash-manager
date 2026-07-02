---
name: java
description: Clean Java code conventions, design patterns, and best practices. Use when writing or reviewing Java code to ensure consistency, readability, and maintainability across the Petty Cash Manager backend.
---

# java

Java coding standards and design patterns for the Petty Cash Manager application.

## When to run

Invoke this skill whenever:
- Writing new Java classes, methods, or interfaces.
- Reviewing or refactoring existing Java code.
- Making design decisions about class hierarchies, patterns, or data structures.
- Choosing between implementation approaches.

## Workflow

### Step 1 — Naming Conventions

| Element      | Convention           | Example                        |
|-------------|----------------------|--------------------------------|
| Package     | lowercase, dot-sep   | `com.pettycash.service`        |
| Class       | PascalCase, noun     | `TransactionService`           |
| Interface   | PascalCase, adjective/noun | `Exportable`, `ReportGenerator` |
| Method      | camelCase, verb      | `calculateBalance()`           |
| Constant    | UPPER_SNAKE          | `MAX_RECEIPT_SIZE`             |
| Variable    | camelCase, descriptive | `pendingReceipts`             |
| Enum        | PascalCase class, UPPER values | `TransactionType.EXPENSE` |

### Step 2 — Money Handling

**Critical:** All monetary values MUST use `BigDecimal`. Never use `double` or `float`.

```java
// ✅ Correct
BigDecimal amount = new BigDecimal("1500.50");
BigDecimal total = amount.add(tax);
int comparison = balance.compareTo(BigDecimal.ZERO);

// ❌ Wrong — floating point precision errors
double amount = 1500.50;
float total = amount + tax;
```

Use `BigDecimal.valueOf()` for numeric literals and `new BigDecimal(String)` for string inputs:
```java
BigDecimal threshold = BigDecimal.valueOf(2000);
BigDecimal parsed = new BigDecimal(userInput); // from string
```

### Step 3 — Immutability and Records

Prefer Java records for DTOs and value objects:

```java
public record TransactionRequest(
    @NotNull TransactionType type,
    @NotNull @Positive BigDecimal amount,
    @NotBlank String description,
    @NotNull LocalDate date,
    String category,
    String paidBy
) {}

public record TransactionResponse(
    Long id,
    TransactionType type,
    BigDecimal amount,
    String description,
    LocalDate date,
    String category,
    String paidBy,
    ReceiptStatus receiptStatus,
    String receiptNo
) {}
```

**Rules:**
1. Use records for all DTOs, API responses, and value objects.
2. Use `@Builder` (Lombok) on entities only if records are insufficient.
3. Make collections unmodifiable when returning from methods: `List.copyOf()`, `Collections.unmodifiableList()`.

### Step 4 — Enums Over Magic Strings

```java
public enum TransactionType {
    EXPENSE, TOPUP;
}

public enum ReceiptStatus {
    PENDING, RECEIVED, NA;
}

// ✅ Type-safe comparison
if (transaction.getType() == TransactionType.TOPUP) { ... }

// ❌ Fragile string comparison
if (transaction.getType().equals("topup")) { ... }
```

### Step 5 — Stream API and Optional

Use streams for collection processing and `Optional` for nullable returns:

```java
// Stream for aggregation
BigDecimal totalExpenses = transactions.stream()
    .filter(t -> t.getType() == TransactionType.EXPENSE)
    .map(Transaction::getAmount)
    .reduce(BigDecimal.ZERO, BigDecimal::add);

// Optional for safe lookups
public Optional<Transaction> findById(Long id) {
    return transactionRepository.findById(id);
}

// Never use Optional as a field or method parameter
// ❌ private Optional<String> category;
// ✅ private String category; // nullable
```

### Step 6 — Exception Handling

Create domain-specific exceptions:

```java
public class InsufficientBalanceException extends RuntimeException {
    private final BigDecimal requested;
    private final BigDecimal available;

    public InsufficientBalanceException(BigDecimal requested, BigDecimal available) {
        super("Insufficient balance: requested %s but only %s available"
            .formatted(requested, available));
        this.requested = requested;
        this.available = available;
    }
}
```

**Rules:**
1. Extend `RuntimeException` for unchecked business exceptions.
2. Include relevant context (amounts, IDs) in exception messages.
3. Never catch `Exception` or `Throwable` broadly — catch specific types.
4. Never swallow exceptions silently (empty catch blocks).

### Step 7 — Design Patterns to Apply

| Pattern    | Use Case                                              |
|-----------|-------------------------------------------------------|
| Builder   | Complex object construction (multi-field transactions)|
| Strategy  | Pluggable export formats (CSV, JSON, PDF)             |
| Factory   | Creating transactions by type (expense vs topup)      |
| Observer  | Event publishing (low-balance alerts, audit logs)     |
| Repository| Data access abstraction (Spring Data JPA)             |

### Step 8 — Code Quality Checklist

Before committing any Java code, verify:
- [ ] No raw types (use `List<Transaction>`, not `List`).
- [ ] No `null` returns where `Optional` is appropriate.
- [ ] All public methods have Javadoc.
- [ ] No hardcoded strings — use constants or enums.
- [ ] `equals()` and `hashCode()` are consistent on entities.
- [ ] `BigDecimal` used for all monetary values.
- [ ] Lombok annotations are minimal (`@RequiredArgsConstructor`, `@Getter` — avoid `@Data` on entities).
