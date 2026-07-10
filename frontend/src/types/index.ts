export type Role = 'ADMIN' | 'USER';
export type UserStatus = 'ACTIVE' | 'INACTIVE';

export interface UserResponse {
  id: string;
  email: string;
  name: string;
  role: Role;
  status: UserStatus;
}

export interface TokenResponse {
  token: string;
  user: UserResponse;
}

export interface SubcategoryResponse {
  id: number;
  name: string;
  categoryId: number;
}

export interface CategoryResponse {
  id: number;
  name: string;
  subcategories: SubcategoryResponse[];
}

export type TransactionType = 'TOPUP' | 'EXPENSE';
export type ReceiptStatus = 'PENDING' | 'RECEIVED' | 'NA';

export interface TransactionResponse {
  id: number;
  transactionNo: string;
  type: TransactionType;
  amount: number;
  description: string;
  date: string;
  timestamp: string;
  payer: string;
  payee: string | null;
  categoryId: number | null;
  categoryName: string | null;
  subcategoryId: number | null;
  subcategoryName: string | null;
  receiptStatus: ReceiptStatus;
  receiptFileId: string | null;
  receiptName: string | null;
  voucherFileId: string | null;
  voucherNumber: string;
  company: string;
  editable: boolean;
}

export interface CashBoxResponse {
  balance: number;
  lowThreshold: number;
}

export interface ExpenseTemplate {
  id: number;
  name: string;
  category: string;
  description: string | null;
  amount: number;
  receiptRequired: boolean;
}

export interface AppConfig {
  demoLoginEnabled: boolean;
  googleClientId: string;
  demoUsers: {
    id: string;
    name: string;
    email: string;
  }[];
  companies: string[];
}

export interface DashboardStatsResponse {
  balance: number;
  lowThreshold: number;
  currentMonthSpent: number;
  currentMonthSpentCount: number;
  currentMonthAdded: number;
  pendingReceiptsCount: number;
  pendingReceiptsValue: number;
  monthlyFlows: {
    month: string;
    spent: number;
    added: number;
  }[];
  categorySpends: {
    name: string;
    value: number;
  }[];
}

export interface Page<T> {
  content: T[];
  totalPages: number;
  totalElements: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
  numberOfElements: number;
  empty: boolean;
}
