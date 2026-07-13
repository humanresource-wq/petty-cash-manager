import axios from 'axios';
import type {
  AppConfig,
  TokenResponse,
  UserResponse,
  TransactionResponse,
  CashBoxResponse,
  CategoryResponse,
  SubcategoryResponse,
  ExpenseTemplate,
  ReceiptStatus,
  UserStatus,
  Role,
  DashboardStatsResponse,
  Page,
} from '../types';

const BASE_URL = import.meta.env.VITE_API_URL || '/api/v1';

const client = axios.create({
  baseURL: BASE_URL,
});

// Attach Authorization Bearer token to requests
client.interceptors.request.use((config) => {
  const token = sessionStorage.getItem('token');
  if (token && config.headers) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
}, (error) => {
  return Promise.reject(error);
});

// Handle global API failures
client.interceptors.response.use(
  (response) => response,
  (error) => {
    let errorMessage = 'An unexpected error occurred';
    if (error.response?.data?.message) {
      errorMessage = error.response.data.message;
    } else if (error.response?.data?.detail) {
      errorMessage = error.response.data.detail;
    } else if (error.message) {
      errorMessage = error.message;
    }
    return Promise.reject(new Error(errorMessage));
  }
);

export const api = {
  auth: {
    getConfig: () => client.get<AppConfig>('/auth/config').then((r) => r.data),
    loginGoogle: (credential: string) =>
      client.post<TokenResponse>('/auth/google', { credential }).then((r) => r.data),
    loginDemo: (userId: string) =>
      client.post<TokenResponse>('/auth/demo', { userId }).then((r) => r.data),
    getMe: () => client.get<UserResponse>('/auth/me').then((r) => r.data),
  },

  transactions: {
    list: (params?: {
      page?: number;
      size?: number;
      startDate?: string;
      endDate?: string;
      type?: string;
      categoryName?: string;
      search?: string;
    }) => client.get<Page<TransactionResponse>>('/transactions', { params }).then((r) => r.data),
    record: (formData: FormData) =>
      client.post<TransactionResponse>('/transactions', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      }).then((r) => r.data),
    update: (id: number, data: {
      amount: number;
      description: string;
      date: string;
      payee?: string;
      categoryId?: number | null;
      subcategoryId?: number | null;
      voucherNumber: string;
      company: string;
    }) => client.put<TransactionResponse>(`/transactions/${id}`, data).then((r) => r.data),
    downloadReceipt: (id: number) =>
      client.get(`/transactions/${id}/receipt`, { responseType: 'blob' }).then((r) => r.data),
    downloadVoucher: (id: number) =>
      client.get(`/transactions/${id}/voucher`, { responseType: 'blob' }).then((r) => r.data),
    updateReceiptStatus: (id: number, status: ReceiptStatus) =>
      client.put<TransactionResponse>(`/transactions/${id}/receipt-status`, null, {
        params: { status },
      }).then((r) => r.data),
    getCashbox: () => client.get<CashBoxResponse>('/transactions/cashbox').then((r) => r.data),
    updateThreshold: (threshold: number) =>
      client.put<CashBoxResponse>('/transactions/cashbox/threshold', null, {
        params: { threshold },
      }).then((r) => r.data),
    getDashboardStats: () => client.get<DashboardStatsResponse>('/transactions/dashboard-stats').then((r) => r.data),
    exportCsv: (params?: {
      startDate?: string;
      endDate?: string;
      type?: string;
      categoryName?: string;
      receiptStatus?: string;
      search?: string;
    }) => client.get('/transactions/export/csv', { params, responseType: 'blob' }).then((r) => r.data),
    exportPdf: (params?: {
      startDate?: string;
      endDate?: string;
      type?: string;
      categoryName?: string;
      receiptStatus?: string;
      search?: string;
    }) => client.get('/transactions/export/pdf', { params, responseType: 'blob' }).then((r) => r.data),
    exportVouchers: (params?: {
      startDate?: string;
      endDate?: string;
    }) => client.get('/transactions/export/vouchers', { params, responseType: 'blob' }).then((r) => r.data),
  },

  categories: {
    list: () => client.get<CategoryResponse[]>('/categories').then((r) => r.data),
    createCategory: (name: string) =>
      client.post<CategoryResponse>('/categories', null, { params: { name } }).then((r) => r.data),
    createSubcategory: (categoryId: number, name: string) =>
      client.post<SubcategoryResponse>(`/categories/${categoryId}/subcategories`, null, {
        params: { name },
      }).then((r) => r.data),
    updateCategory: (id: number, name: string) =>
      client.put<CategoryResponse>(`/categories/${id}`, null, { params: { name } }).then((r) => r.data),
    updateSubcategory: (id: number, name: string) =>
      client.put<SubcategoryResponse>(`/categories/subcategories/${id}`, null, {
        params: { name },
      }).then((r) => r.data),
    deleteCategory: (id: number) => client.delete<void>(`/categories/${id}`).then((r) => r.data),
    deleteSubcategory: (id: number) =>
      client.delete<void>(`/categories/subcategories/${id}`).then((r) => r.data),
  },

  templates: {
    list: () => client.get<ExpenseTemplate[]>('/templates').then((r) => r.data),
    create: (data: {
      name: string;
      category: string;
      description?: string;
      amount: number;
      receiptRequired: boolean;
    }) => client.post<ExpenseTemplate>('/templates', data).then((r) => r.data),
    update: (id: number, data: {
      name: string;
      category: string;
      description?: string;
      amount: number;
      receiptRequired: boolean;
    }) => client.put<ExpenseTemplate>(`/templates/${id}`, data).then((r) => r.data),
    delete: (id: number) => client.delete<void>(`/templates/${id}`).then((r) => r.data),
  },

  users: {
    list: () => client.get<UserResponse[]>('/users').then((r) => r.data),
    create: (data: { name: string; email: string; role: Role }) =>
      client.post<UserResponse>('/users', data).then((r) => r.data),
    updateStatus: (id: string, status: UserStatus) =>
      client.put<UserResponse>(`/users/${id}/status`, null, { params: { status } }).then((r) => r.data),
  },
};
export default api;
