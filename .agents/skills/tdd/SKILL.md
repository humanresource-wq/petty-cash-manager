---
name: tdd
description: Red → Green → Refactor cycle. Write failing tests first, implement minimal code to pass, then refactor. Use for all new features, bug fixes, and refactoring across backend (JUnit 5 / Mockito) and frontend (Jest / React Testing Library).
---

# tdd

Test-Driven Development workflow for the Petty Cash Manager application. Apply the Red → Green → Refactor cycle to ensure correctness, prevent regressions, and produce clean, well-tested code.

## When to run

Invoke this skill whenever:
- Implementing a new feature (API endpoint, service method, React component, or utility).
- Fixing a bug — write a test that reproduces the bug first.
- Refactoring existing code — ensure test coverage exists before changing structure.
- Adding or modifying business rules (e.g., balance calculations, threshold alerts, receipt tracking).

## Workflow

### Step 1 — Red: Write a Failing Test

Before writing any production code, create a test that describes the expected behavior.

**Backend (JUnit 5 + Mockito):**
```java
@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private TransactionService transactionService;

    @Test
    @DisplayName("should calculate correct balance from mixed transactions")
    void shouldCalculateBalance() {
        // Arrange — set up test data
        List<Transaction> transactions = List.of(
            new Transaction(TransactionType.TOPUP, BigDecimal.valueOf(10000)),
            new Transaction(TransactionType.EXPENSE, BigDecimal.valueOf(3500))
        );
        when(transactionRepository.findAll()).thenReturn(transactions);

        // Act
        BigDecimal balance = transactionService.calculateBalance();

        // Assert
        assertThat(balance).isEqualByComparingTo(BigDecimal.valueOf(6500));
    }
}
```

**Frontend (Jest + React Testing Library):**
```javascript
import { render, screen, fireEvent } from '@testing-library/react';
import Dashboard from './Dashboard';

test('displays low balance warning when below threshold', () => {
  const state = { transactions: [], lowThreshold: 2000 };
  render(<Dashboard state={state} openTxModal={jest.fn()} />);
  expect(screen.getByText(/low balance/i)).toBeInTheDocument();
});
```

Run the test and confirm it **fails** (Red).

```bash
# Backend
./mvnw test -pl backend -Dtest=TransactionServiceTest

# Frontend
cd frontend && npx react-scripts test --watchAll=false --testPathPattern=Dashboard
```

### Step 2 — Green: Write Minimal Code to Pass

Implement only the code required to make the failing test pass.

- Do **not** add extra features, optimizations, or abstractions.
- Do **not** skip edge cases if the test covers them.
- Focus on making the test green with the simplest correct implementation.

Run the test again and confirm it **passes** (Green).

### Step 3 — Refactor: Improve Without Changing Behavior

With the test passing, improve the code:

1. **Extract methods** for readability.
2. **Remove duplication** between test or production code.
3. **Apply design patterns** (e.g., Strategy for category-based calculations, Builder for transaction creation).
4. **Rename** variables and methods for clarity.

After every refactor step, re-run the full test suite:
```bash
# Backend — full suite
./mvnw test -pl backend

# Frontend — full suite
cd frontend && npx react-scripts test --watchAll=false
```

### Step 4 — Verify Coverage and Edge Cases

After the Red → Green → Refactor cycle, add tests for:

- **Edge cases:** zero amounts, negative values, empty transaction lists, null inputs.
- **Boundary conditions:** exact threshold amount, date boundaries (month-end, year-end).
- **Error paths:** invalid CSV imports, malformed JSON backups, duplicate categories/members.
- **Authorization gates (backend):** unauthenticated access returns 401, unauthorized role returns 403.

### Step 5 — Report Test Results

List all test results, confirm 100% pass rate, and summarize:
- Total tests run (backend + frontend).
- Any new test files created.
- Coverage gaps identified for future work.
