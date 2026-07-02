---
name: react-typescript
description: Guide for React component patterns, custom hooks, TypeScript types, form handling, routing, state management, and API integration. Use when building or modifying frontend components, hooks, or UI logic in the Petty Cash Manager frontend.
---

# react-typescript

Best practices for React components, TypeScript types, hooks, forms, and UI logic in the Petty Cash Manager frontend.

## When to run

Invoke this skill whenever:
- Creating or modifying React components in `frontend/src/components/`.
- Adding custom hooks in `frontend/src/hooks/`.
- Defining TypeScript types/interfaces in `frontend/src/types/`.
- Implementing form handling, validation, or API integration.
- Setting up routing or state management.

## Context

The current frontend uses React 18 with JavaScript and `react-scripts`. The plan is to migrate to TypeScript and add a proper API layer. Existing components include:
- [Dashboard.js](file:///home/harsh/petty-cash-manager/src/components/Dashboard.js) — Balance overview, charts, alerts.
- [Transactions.js](file:///home/harsh/petty-cash-manager/src/components/Transactions.js) — Transaction list with filters.
- [TransactionModal.js](file:///home/harsh/petty-cash-manager/src/components/TransactionModal.js) — Add/edit transaction form.
- [Settings.js](file:///home/harsh/petty-cash-manager/src/components/Settings.js) — Categories, members, threshold config.
- [Sidebar.js](file:///home/harsh/petty-cash-manager/src/components/Sidebar.js) — Navigation sidebar.
- [utils.js](file:///home/harsh/petty-cash-manager/src/utils.js) — Utilities (balance calc, formatting, CSV/JSON import/export).

## Workflow

### Step 1 — TypeScript Types

Define strict types for all domain models:

```typescript
// types/transaction.ts
export type TransactionType = 'expense' | 'topup';
export type ReceiptStatus = 'pending' | 'received' | 'na';

export interface Transaction {
  id: string;
  type: TransactionType;
  amount: number;
  description: string;
  date: string;          // ISO date string (YYYY-MM-DD)
  category: string;
  paidBy: string;
  receiptStatus: ReceiptStatus;
  receiptNo: string;
}

export interface AppState {
  transactions: Transaction[];
  categories: string[];
  members: string[];
  lowThreshold: number;
}

// types/api.ts
export interface ApiResponse<T> {
  data: T;
  message?: string;
}

export interface PaginatedResponse<T> extends ApiResponse<T[]> {
  page: number;
  totalPages: number;
  totalItems: number;
}
```

**Rules:**
1. Define types in `frontend/src/types/` — one file per domain.
2. Use `interface` for object shapes, `type` for unions and primitives.
3. Never use `any` — use `unknown` with type guards if needed.
4. Export all types for reuse across components.

### Step 2 — Component Patterns

Use functional components with explicit prop types:

```typescript
// components/TransactionCard.tsx
interface TransactionCardProps {
  transaction: Transaction;
  onEdit: (id: string) => void;
  onDelete: (id: string) => void;
  onToggleReceipt: (id: string) => void;
}

const TransactionCard: React.FC<TransactionCardProps> = ({
  transaction,
  onEdit,
  onDelete,
  onToggleReceipt,
}) => {
  const isExpense = transaction.type === 'expense';

  return (
    <div className={`tx-card ${isExpense ? 'tx-expense' : 'tx-topup'}`}>
      <span className="tx-amount">
        {isExpense ? '-' : '+'}{formatCurrency(transaction.amount)}
      </span>
      <span className="tx-desc">{transaction.description}</span>
      <div className="tx-actions">
        <button onClick={() => onEdit(transaction.id)}>Edit</button>
        <button onClick={() => onDelete(transaction.id)}>Delete</button>
      </div>
    </div>
  );
};
```

**Rules:**
1. One component per file, named same as component.
2. Define props interface directly above the component.
3. Destructure props in the function signature.
4. Use `React.FC<Props>` or explicit return type annotation.
5. Keep components focused — under 150 lines. Extract sub-components if larger.

### Step 3 — Custom Hooks

Extract reusable logic into custom hooks:

```typescript
// hooks/useTransactions.ts
export function useTransactions() {
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchTransactions = useCallback(async (filters?: TransactionFilters) => {
    setLoading(true);
    setError(null);
    try {
      const data = await api.getTransactions(filters);
      setTransactions(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load transactions');
    } finally {
      setLoading(false);
    }
  }, []);

  const createTransaction = useCallback(async (request: TransactionRequest) => {
    const created = await api.createTransaction(request);
    setTransactions(prev => [created, ...prev]);
    return created;
  }, []);

  return { transactions, loading, error, fetchTransactions, createTransaction };
}
```

**Rules:**
1. Prefix all custom hooks with `use`.
2. Return objects (not arrays) for hooks with 3+ values.
3. Handle loading, error, and data states.
4. Use `useCallback` for functions returned from hooks.

### Step 4 — Form Handling

Use controlled components with validation:

```typescript
// hooks/useForm.ts
export function useForm<T extends Record<string, unknown>>(
  initialValues: T,
  validate: (values: T) => Partial<Record<keyof T, string>>
) {
  const [values, setValues] = useState(initialValues);
  const [errors, setErrors] = useState<Partial<Record<keyof T, string>>>({});
  const [touched, setTouched] = useState<Partial<Record<keyof T, boolean>>>({});

  const handleChange = (field: keyof T, value: T[keyof T]) => {
    setValues(prev => ({ ...prev, [field]: value }));
    if (touched[field]) {
      const fieldErrors = validate({ ...values, [field]: value });
      setErrors(prev => ({ ...prev, [field]: fieldErrors[field] }));
    }
  };

  const handleBlur = (field: keyof T) => {
    setTouched(prev => ({ ...prev, [field]: true }));
    const fieldErrors = validate(values);
    setErrors(prev => ({ ...prev, [field]: fieldErrors[field] }));
  };

  const handleSubmit = (onSubmit: (values: T) => void) => (e: React.FormEvent) => {
    e.preventDefault();
    const validationErrors = validate(values);
    setErrors(validationErrors);
    setTouched(Object.keys(values).reduce((acc, key) =>
      ({ ...acc, [key]: true }), {} as Record<keyof T, boolean>));
    if (Object.keys(validationErrors).length === 0) {
      onSubmit(values);
    }
  };

  return { values, errors, touched, handleChange, handleBlur, handleSubmit, setValues };
}
```

### Step 5 — API Layer

Centralize API calls with type safety:

```typescript
// api/client.ts
const BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api/v1';

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await fetch(`${BASE_URL}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });
  if (!response.ok) {
    const error = await response.json().catch(() => ({ message: 'Request failed' }));
    throw new Error(error.message);
  }
  return response.json();
}

export const api = {
  getTransactions: (filters?: TransactionFilters) =>
    request<Transaction[]>(`/transactions?${new URLSearchParams(filters as any)}`),

  createTransaction: (data: TransactionRequest) =>
    request<Transaction>('/transactions', {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  deleteTransaction: (id: string) =>
    request<void>(`/transactions/${id}`, { method: 'DELETE' }),
};
```

### Step 6 — Verify

After any frontend changes:
```bash
# Type check
cd frontend && npx tsc --noEmit

# Run tests
cd frontend && npm test -- --watchAll=false

# Dev server
cd frontend && npm run dev
```
