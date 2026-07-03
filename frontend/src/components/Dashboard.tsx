import React, { useState, useEffect } from 'react';
import axios from 'axios';
import { api } from '../api/client';
import type {
  TransactionResponse,
  CategoryResponse,
  ExpenseTemplate,
  UserResponse,
  CashBoxResponse,
  ReceiptStatus,
  DashboardStatsResponse,
} from '../types';
import { TransactionModal } from './TransactionModal';
import { AdminPanel } from './AdminPanel';
import {
  ResponsiveContainer,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip as ChartTooltip,
  Legend,
  PieChart,
  Pie,
  Cell,
} from 'recharts';

interface DashboardProps {
  currentUser: UserResponse;
  onLogout: () => void;
}

const PALETTE = ['#6366f1', '#a855f7', '#ec4899', '#f59e0b', '#10b981', '#06b6d4', '#f43f5e', '#84cc16'];

export const Dashboard: React.FC<DashboardProps> = ({ currentUser, onLogout }) => {
  const [activeTab, setActiveTab] = useState<'dashboard' | 'transactions' | 'settings'>('dashboard');
  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const [toastTimeoutId, setToastTimeoutId] = useState<any>(null);

  // Core Data States
  const [transactions, setTransactions] = useState<TransactionResponse[]>([]);
  const [categories, setCategories] = useState<CategoryResponse[]>([]);
  const [templates, setTemplates] = useState<ExpenseTemplate[]>([]);
  const [cashbox, setCashbox] = useState<CashBoxResponse>({ balance: 0, lowThreshold: 2000 });
  const [dashboardStats, setDashboardStats] = useState<DashboardStatsResponse | null>(null);
  const [loading, setLoading] = useState<boolean>(true);

  // Filters for Transactions Tab
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [filterType, setFilterType] = useState<string>('');
  const [filterCategory, setFilterCategory] = useState<string>('');
  const [filterReceipt, setFilterReceipt] = useState<string>('');
  const [filterMonth, setFilterMonth] = useState<string>('');

  // Modals
  const [isTxModalOpen, setIsTxModalOpen] = useState<boolean>(false);
  const [txModalDefaultType, setTxModalDefaultType] = useState<'EXPENSE' | 'TOPUP'>('EXPENSE');
  const [uploadingReceiptTxId, setUploadingReceiptTxId] = useState<number | null>(null);

  useEffect(() => {
    fetchInitialData();
  }, []);

  const fetchInitialData = async () => {
    setLoading(true);
    try {
      const [txData, catData, tempData, statsData] = await Promise.all([
        api.transactions.list(),
        api.categories.list(),
        api.templates.list(),
        api.transactions.getDashboardStats(),
      ]);
      setTransactions(txData);
      setCategories(catData);
      setTemplates(tempData);
      setDashboardStats(statsData);
      setCashbox({ balance: statsData.balance, lowThreshold: statsData.lowThreshold });
    } catch (err) {
      showToast('❌ Failed to fetch database state: ' + (err instanceof Error ? err.message : String(err)));
    } finally {
      setLoading(false);
    }
  };

  const showToast = (msg: string) => {
    setToastMessage(msg);
    if (toastTimeoutId) clearTimeout(toastTimeoutId);
    const id = setTimeout(() => setToastMessage(null), 2500);
    setToastTimeoutId(id);
  };

  // Helper: browser download triggers
  const triggerDownload = (blobBytes: Blob, filename: string) => {
    const url = window.URL.createObjectURL(blobBytes);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => window.URL.revokeObjectURL(url), 60000);
  };

  const handleDownloadReceipt = async (txId: number, filename: string) => {
    try {
      showToast('📥 Downloading receipt...');
      const blob = await api.transactions.downloadReceipt(txId);
      triggerDownload(blob, filename || `receipt-${txId}`);
    } catch (err) {
      showToast('❌ Failed: ' + (err instanceof Error ? err.message : String(err)));
    }
  };

  const handleDownloadVoucher = async (txId: number, txNo: string) => {
    try {
      showToast('📄 Generating A5 landscape voucher...');
      const blob = await api.transactions.downloadVoucher(txId);
      triggerDownload(blob, `voucher-${txNo}.pdf`);
      showToast('✅ Voucher PDF downloaded!');
      fetchInitialData(); // refresh list to load voucherFileId
    } catch (err) {
      showToast('❌ Failed to generate voucher: ' + (err instanceof Error ? err.message : String(err)));
    }
  };

  const handleToggleReceiptStatus = async (id: number, currentStatus: ReceiptStatus) => {
    if (currentUser.role !== 'ADMIN') return;
    const nextStatus: ReceiptStatus = currentStatus === 'PENDING' ? 'RECEIVED' : 'PENDING';
    try {
      await api.transactions.updateReceiptStatus(id, nextStatus);
      showToast(`🧾 Receipt status toggled to ${nextStatus.toLowerCase()}`);
      fetchInitialData();
    } catch (err) {
      showToast('❌ Fail: ' + (err instanceof Error ? err.message : String(err)));
    }
  };

  const handleReceiptBadgeClick = (tx: TransactionResponse) => {
    if (tx.receiptStatus === 'RECEIVED') {
      handleDownloadReceipt(tx.id, tx.receiptName || 'receipt');
    } else if (tx.receiptStatus === 'PENDING') {
      // Click pending badge to trigger file upload dialog
      setUploadingReceiptTxId(tx.id);
      document.getElementById('receipt-badge-file-uploader')?.click();
    }
  };

  const handleBadgeFileSelected = async (e: React.ChangeEvent<HTMLInputElement>) => {
    if (!uploadingReceiptTxId || !e.target.files || !e.target.files[0]) return;
    const file = e.target.files[0];
    showToast('📥 Uploading receipt attachment...');
    try {
      const formData = new FormData();
      formData.append('file', file);
      // We had a separate uploadReceipt endpoint on the backend: POST /api/v1/transactions/{id}/receipt
      // Wait, let's verify if we kept it. Yes, we did: @PostMapping(value = "/{id}/receipt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
      // Let's call it:
      await axiosUploadReceiptHelper(uploadingReceiptTxId, file);
      showToast('✅ Receipt uploaded successfully!');
      fetchInitialData();
    } catch (err) {
      showToast('❌ Upload failed: ' + (err instanceof Error ? err.message : String(err)));
    } finally {
      setUploadingReceiptTxId(null);
      e.target.value = '';
    }
  };

  const axiosUploadReceiptHelper = async (txId: number, file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    const token = sessionStorage.getItem('token');
    await axios.post(`/api/v1/transactions/${txId}/receipt`, formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
        'Authorization': `Bearer ${token}`
      }
    });
  };

  // --- Derived Calculations ---
  const currentMonthName = () => {
    return new Date().toLocaleDateString('en-IN', { month: 'long' });
  };

  const currentMonthSpent = () => {
    return dashboardStats?.currentMonthSpent ?? 0;
  };

  const currentMonthAdded = () => {
    return dashboardStats?.currentMonthAdded ?? 0;
  };

  const pendingReceiptsCount = () => {
    return dashboardStats?.pendingReceiptsCount ?? 0;
  };

  const pendingReceiptsValue = () => {
    return dashboardStats?.pendingReceiptsValue ?? 0;
  };

  // --- Charts Data ---
  const getTrendData = () => {
    if (!dashboardStats) return [];
    return dashboardStats.monthlyFlows.map((f) => ({
      month: f.month,
      Spent: f.spent,
      Added: f.added,
    }));
  };

  const getPieData = () => {
    if (!dashboardStats) return [];
    return dashboardStats.categorySpends;
  };

  const getTopSpendAreas = () => {
    const pie = getPieData().slice(0, 5);
    const max = pie.length ? pie[0].value : 1;
    return pie.map((p) => ({
      name: p.name,
      value: p.value,
      percentage: (p.value / max) * 105, // slightly adjusted scaling
    }));
  };

  // --- Filtered Transactions list ---
  const getFilteredTransactions = () => {
    let list = [...transactions];
    if (searchQuery) {
      const q = searchQuery.toLowerCase();
      list = list.filter(
        (t) =>
          t.description.toLowerCase().includes(q) ||
          t.payer.toLowerCase().includes(q) ||
          t.payee?.toLowerCase().includes(q) ||
          t.transactionNo.toLowerCase().includes(q)
      );
    }
    if (filterType) {
      list = list.filter((t) => t.type === filterType);
    }
    if (filterCategory) {
      list = list.filter((t) => t.categoryName === filterCategory);
    }
    if (filterReceipt) {
      list = list.filter((t) => t.receiptStatus === filterReceipt);
    }
    if (filterMonth) {
      list = list.filter((t) => t.date.startsWith(filterMonth));
    }
    return list;
  };

  return (
    <div className="flex min-h-screen bg-slate-950 text-slate-100 font-sans">
      {/* Sidebar Navigation */}
      <aside className="w-64 bg-slate-900 border-r border-slate-800 flex flex-col p-5 gap-6 shrink-0 sticky top-0 h-screen justify-between">
        <div className="flex flex-col gap-6">
          <div className="logo flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-indigo-500 to-purple-600 flex items-center justify-center text-xl shadow-lg">
              💸
            </div>
            <div>
              <h1 className="text-sm font-extrabold text-white leading-tight">Petty Cash</h1>
              <span className="text-[10px] text-indigo-400 font-semibold tracking-wider uppercase">
                {currentUser.role} Account
              </span>
            </div>
          </div>

          <div className="flex flex-col gap-1.5">
            <button
              onClick={() => setActiveTab('dashboard')}
              className={`flex items-center gap-3 w-full py-2.5 px-3 rounded-lg text-xs font-bold transition text-left ${
                activeTab === 'dashboard'
                  ? 'bg-indigo-600 text-white shadow-lg shadow-indigo-600/20'
                  : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/40'
              }`}
            >
              <span className="text-sm">📊</span> Dashboard Overview
            </button>
            <button
              onClick={() => setActiveTab('transactions')}
              className={`flex items-center gap-3 w-full py-2.5 px-3 rounded-lg text-xs font-bold transition text-left ${
                activeTab === 'transactions'
                  ? 'bg-indigo-600 text-white shadow-lg shadow-indigo-600/20'
                  : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/40'
              }`}
            >
              <span className="text-sm">🧾</span> Transactions Ledger
            </button>
            {currentUser.role === 'ADMIN' && (
              <button
                onClick={() => setActiveTab('settings')}
                className={`flex items-center gap-3 w-full py-2.5 px-3 rounded-lg text-xs font-bold transition text-left ${
                  activeTab === 'settings'
                    ? 'bg-indigo-600 text-white shadow-lg shadow-indigo-600/20'
                    : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/40'
                }`}
              >
                <span className="text-sm">⚙️</span> Configurations
              </button>
            )}
          </div>
        </div>

        {/* User Card & Logout */}
        <div className="border-t border-slate-800/60 pt-4 flex flex-col gap-3">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-full bg-slate-800 flex items-center justify-center font-bold text-xs text-indigo-400 border border-slate-700">
              {currentUser.name.charAt(0)}
            </div>
            <div className="flex flex-col min-w-0">
              <span className="text-xs font-bold text-white truncate">{currentUser.name}</span>
              <span className="text-[10px] text-slate-500 truncate">{currentUser.email}</span>
            </div>
          </div>
          <button
            onClick={onLogout}
            className="w-full py-2 px-3 bg-red-950/20 hover:bg-red-950/40 border border-red-900/30 hover:border-red-900/60 text-red-400 hover:text-red-300 font-bold text-xs rounded-lg transition"
          >
            Sign Out
          </button>
        </div>
      </aside>

      {/* Main Workspace */}
      <main className="flex-1 p-8 max-w-6.5xl mx-auto overflow-y-auto">
        {loading ? (
          <div className="min-h-[60vh] flex flex-col items-center justify-center gap-3 text-slate-400">
            <span className="w-8 h-8 border-4 border-slate-800 border-t-indigo-500 rounded-full animate-spin"></span>
            <span className="text-xs font-semibold">Resuming secure database session...</span>
          </div>
        ) : (
          <>
            {/* ============ VIEW: DASHBOARD ============ */}
            {activeTab === 'dashboard' && (
              <section className="flex flex-col gap-6 animate-[pop_0.15s_ease]">
                <div className="flex items-center justify-between flex-wrap gap-4 border-b border-slate-800/40 pb-4">
                  <div>
                    <h2 className="text-xl font-extrabold text-white tracking-tight">Dashboard Overview</h2>
                    <p className="text-xs text-slate-400 mt-0.5">
                      {new Date().toLocaleDateString('en-IN', {
                        weekday: 'long',
                        day: 'numeric',
                        month: 'long',
                        year: 'numeric',
                      })}
                    </p>
                  </div>
                  <div className="flex gap-2.5">
                    {currentUser.role === 'ADMIN' && (
                      <button
                        onClick={() => {
                          setTxModalDefaultType('TOPUP');
                          setIsTxModalOpen(true);
                        }}
                        className="bg-emerald-600 hover:bg-emerald-500 text-white font-bold text-xs py-2.5 px-4 rounded-lg shadow-lg shadow-emerald-600/10 hover:shadow-emerald-500/20 hover:-translate-y-[1px] active:translate-y-0 transition duration-150"
                      >
                        ＋ Add Cash
                      </button>
                    )}
                    <button
                      onClick={() => {
                        setTxModalDefaultType('EXPENSE');
                        setIsTxModalOpen(true);
                      }}
                      className="bg-indigo-600 hover:bg-indigo-500 text-white font-bold text-xs py-2.5 px-4 rounded-lg shadow-lg shadow-indigo-600/20 hover:shadow-indigo-500/30 hover:-translate-y-[1px] active:translate-y-0 transition duration-150"
                    >
                      ＋ Record Expense
                    </button>
                  </div>
                </div>

                {/* Stats Metric Cards Grid */}
                <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
                  {/* Balance HERO metric */}
                  <div className="relative overflow-hidden bg-gradient-to-br from-indigo-700 to-purple-800 rounded-xl p-5 shadow-lg flex flex-col justify-between min-h-[110px] group">
                    <div className="absolute right-[-20px] top-[-20px] w-24 h-24 rounded-full bg-white/10 group-hover:scale-115 transition duration-300"></div>
                    <span className="text-[10px] font-bold text-white/70 uppercase tracking-widest">
                      Cash in Hand
                    </span>
                    <h3 className="text-2xl font-extrabold text-white tracking-tight mt-2">
                      ₹{cashbox.balance.toLocaleString('en-IN', { minimumFractionDigits: 2 })}
                    </h3>
                    <span className="text-[10px] text-white/80 font-medium mt-1 select-none">
                      {cashbox.balance < cashbox.lowThreshold ? (
                        <span className="text-amber-300 font-bold animate-pulse">
                          ⚠️ Below ₹{cashbox.lowThreshold.toLocaleString()} warning
                        </span>
                      ) : (
                        'Available inside cash drawer'
                      )}
                    </span>
                  </div>

                  <div className="bg-slate-900 border border-slate-800 rounded-xl p-5 flex flex-col justify-between min-h-[110px]">
                    <span className="text-[10px] font-bold text-slate-500 uppercase tracking-widest">
                      Spent · {currentMonthName()}
                    </span>
                    <h3 className="text-2xl font-extrabold text-red-500 tracking-tight mt-2">
                      ₹{currentMonthSpent().toLocaleString('en-IN', { minimumFractionDigits: 2 })}
                    </h3>
                    <span className="text-[10px] text-slate-400 mt-1 font-medium">
                      Month-to-date claim payments
                    </span>
                  </div>

                  <div className="bg-slate-900 border border-slate-800 rounded-xl p-5 flex flex-col justify-between min-h-[110px]">
                    <span className="text-[10px] font-bold text-slate-500 uppercase tracking-widest">
                      Replenished · {currentMonthName()}
                    </span>
                    <h3 className="text-2xl font-extrabold text-emerald-500 tracking-tight mt-2">
                      ₹{currentMonthAdded().toLocaleString('en-IN', { minimumFractionDigits: 2 })}
                    </h3>
                    <span className="text-[10px] text-slate-400 mt-1 font-medium">
                      Month-to-date cash additions
                    </span>
                  </div>

                  <div className="bg-slate-900 border border-slate-800 rounded-xl p-5 flex flex-col justify-between min-h-[110px]">
                    <span className="text-[10px] font-bold text-slate-500 uppercase tracking-widest">
                      Pending Receipts
                    </span>
                    <h3 className="text-2xl font-extrabold text-amber-500 tracking-tight mt-2">
                      {pendingReceiptsCount()}
                    </h3>
                    <span className="text-[10px] text-slate-400 mt-1 font-medium">
                      {pendingReceiptsCount() > 0
                        ? `₹${pendingReceiptsValue().toLocaleString()} awaiting verify`
                        : 'All receipts verified! 🎉'}
                    </span>
                  </div>
                </div>

                {/* Charts Grid */}
                <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                  {/* Recharts Monthly trend */}
                  <div className="bg-slate-900 border border-slate-800 rounded-xl p-5 shadow flex flex-col gap-3">
                    <div>
                      <h3 className="text-xs font-bold text-slate-200 uppercase tracking-wider">
                        Monthly Cash Flow
                      </h3>
                      <p className="text-[10px] text-slate-500">Spending vs top-ups · last 6 months</p>
                    </div>
                    <div className="h-64 w-full">
                      <ResponsiveContainer width="100%" height="100%">
                        <BarChart data={getTrendData()} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                          <XAxis dataKey="month" tick={{ fill: '#64748b', fontSize: 10 }} />
                          <YAxis tick={{ fill: '#64748b', fontSize: 10 }} />
                          <ChartTooltip
                            contentStyle={{ backgroundColor: '#0f172a', borderColor: '#334155', color: '#f8fafc' }}
                            labelStyle={{ fontWeight: 'bold' }}
                          />
                          <Legend verticalAlign="bottom" height={36} iconType="circle" iconSize={8} wrapperStyle={{ fontSize: 11 }} />
                          <Bar dataKey="Spent" fill="rgba(239,68,68,0.75)" radius={[4, 4, 0, 0]} maxBarSize={28} />
                          <Bar dataKey="Added" fill="rgba(16,185,129,0.75)" radius={[4, 4, 0, 0]} maxBarSize={28} />
                        </BarChart>
                      </ResponsiveContainer>
                    </div>
                  </div>

                  {/* Recharts Pie category distribution */}
                  <div className="bg-slate-900 border border-slate-800 rounded-xl p-5 shadow flex flex-col gap-3">
                    <div>
                      <h3 className="text-xs font-bold text-slate-200 uppercase tracking-wider">
                        Where the Money Goes
                      </h3>
                      <p className="text-[10px] text-slate-500">Spend distribution by category · all time</p>
                    </div>
                    <div className="h-64 w-full flex items-center justify-center">
                      {getPieData().length > 0 ? (
                        <ResponsiveContainer width="100%" height="100%">
                          <PieChart>
                            <Pie
                              data={getPieData()}
                              dataKey="value"
                              nameKey="name"
                              cx="50%"
                              cy="50%"
                              innerRadius={60}
                              outerRadius={80}
                              paddingAngle={3}
                            >
                              {getPieData().map((_, index) => (
                                <Cell key={`cell-${index}`} fill={PALETTE[index % PALETTE.length]} />
                              ))}
                            </Pie>
                            <ChartTooltip contentStyle={{ backgroundColor: '#0f172a', borderColor: '#334155' }} />
                            <Legend verticalAlign="bottom" height={36} iconType="circle" iconSize={8} wrapperStyle={{ fontSize: 10 }} />
                          </PieChart>
                        </ResponsiveContainer>
                      ) : (
                        <div className="text-xs text-slate-500">No expenses recorded yet.</div>
                      )}
                    </div>
                  </div>
                </div>

                {/* Bottom Recent & Top Spend lists */}
                <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                  {/* Recent Activity List */}
                  <div className="bg-slate-900 border border-slate-800 rounded-xl p-5 flex flex-col gap-3">
                    <div>
                      <h3 className="text-xs font-bold text-slate-200 uppercase tracking-wider">
                        Recent Activity
                      </h3>
                      <p className="text-[10px] text-slate-500">Last 6 ledger updates</p>
                    </div>
                    <div className="flex flex-col min-h-[160px] divide-y divide-slate-800/50">
                      {transactions.slice(0, 6).map((t) => (
                        <div key={t.id} className="flex justify-between items-center py-2.5">
                          <div className="flex flex-col min-w-0">
                            <span className="text-xs font-bold text-slate-200 truncate">{t.description}</span>
                            <span className="text-[10px] text-slate-500">
                              {t.date} · {t.type === 'TOPUP' ? 'Top-up' : t.categoryName} · {t.payer}
                            </span>
                          </div>
                          <span
                            className={`text-xs font-bold ${t.type === 'TOPUP' ? 'text-emerald-500' : 'text-red-500'}`}
                          >
                            {t.type === 'TOPUP' ? '+' : '-'}₹
                            {t.amount.toLocaleString('en-IN', { minimumFractionDigits: 2 })}
                          </span>
                        </div>
                      ))}
                      {transactions.length === 0 && (
                        <div className="text-xs text-slate-500 text-center py-8">
                          No transactions recorded yet.
                        </div>
                      )}
                    </div>
                  </div>

                  {/* Top spend bars list */}
                  <div className="bg-slate-900 border border-slate-800 rounded-xl p-5 flex flex-col gap-3">
                    <div>
                      <h3 className="text-xs font-bold text-slate-200 uppercase tracking-wider">
                        Top Spend Areas
                      </h3>
                      <p className="text-[10px] text-slate-500">All-time category volume</p>
                    </div>
                    <div className="flex flex-col gap-3.5 min-h-[160px] justify-center">
                      {getTopSpendAreas().map((c, index) => (
                        <div key={c.name} className="flex items-center gap-3">
                          <span className="text-xs font-bold text-slate-400 w-24 truncate">{c.name}</span>
                          <div className="flex-1 bg-slate-950 h-2 rounded-full overflow-hidden">
                            <div
                              className="h-full rounded-full"
                              style={{
                                width: `${c.percentage}%`,
                                backgroundColor: PALETTE[index % PALETTE.length],
                              }}
                            ></div>
                          </div>
                          <span className="text-xs font-bold text-slate-200 w-20 text-right">
                            ₹{c.value.toLocaleString('en-IN', { maximumFractionDigits: 0 })}
                          </span>
                        </div>
                      ))}
                      {getTopSpendAreas().length === 0 && (
                        <div className="text-xs text-slate-500 text-center py-8">
                          No category charts available.
                        </div>
                      )}
                    </div>
                  </div>
                </div>
              </section>
            )}

            {/* ============ VIEW: TRANSACTIONS LIST ============ */}
            {activeTab === 'transactions' && (
              <section className="flex flex-col gap-6 animate-[pop_0.15s_ease]">
                <div className="flex items-center justify-between flex-wrap gap-4 border-b border-slate-800/40 pb-4">
                  <div>
                    <h2 className="text-xl font-extrabold text-white tracking-tight">Transactions Ledger</h2>
                    <p className="text-xs text-slate-400 mt-0.5">
                      Every rupee in and out — tap an amber badge to upload/verify receipts.
                    </p>
                  </div>
                  <div className="flex gap-2">
                    <button
                      onClick={() => {
                        setTxModalDefaultType('EXPENSE');
                        setIsTxModalOpen(true);
                      }}
                      className="bg-indigo-600 hover:bg-indigo-500 text-white font-bold text-xs py-2 px-3 rounded-lg shadow-lg hover:shadow-indigo-500/20 hover:-translate-y-[1px] active:translate-y-0 transition duration-150"
                    >
                      ＋ Record Expense
                    </button>
                  </div>
                </div>

                {/* Hidden input to receive badge clicked receipt file select */}
                <input
                  id="receipt-badge-file-uploader"
                  type="file"
                  className="hidden"
                  accept="image/*,application/pdf"
                  onChange={handleBadgeFileSelected}
                />

                {/* Filters Row */}
                <div className="bg-slate-900 border border-slate-800 rounded-xl p-4 flex flex-wrap gap-3 items-center">
                  <input
                    type="search"
                    placeholder="🔍 Search description, payer email, reference no..."
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    className="bg-slate-950 border border-slate-800 rounded-lg py-2 px-3 text-xs text-white placeholder-slate-650 focus:outline-none focus:border-indigo-500 flex-1 min-w-[200px]"
                  />
                  <select
                    value={filterType}
                    onChange={(e) => setFilterType(e.target.value)}
                    className="bg-slate-950 border border-slate-800 rounded-lg py-2 px-3 text-xs text-white focus:outline-none focus:border-indigo-500"
                  >
                    <option value="">All transaction types</option>
                    <option value="EXPENSE">Expenses</option>
                    <option value="TOPUP">Top-ups</option>
                  </select>
                  <select
                    value={filterCategory}
                    onChange={(e) => setFilterCategory(e.target.value)}
                    className="bg-slate-950 border border-slate-800 rounded-lg py-2 px-3 text-xs text-white focus:outline-none focus:border-indigo-500"
                  >
                    <option value="">All categories</option>
                    {categories.map((c) => (
                      <option key={c.id} value={c.name}>
                        {c.name}
                      </option>
                    ))}
                  </select>
                  <select
                    value={filterReceipt}
                    onChange={(e) => setFilterReceipt(e.target.value)}
                    className="bg-slate-950 border border-slate-800 rounded-lg py-2 px-3 text-xs text-white focus:outline-none focus:border-indigo-500"
                  >
                    <option value="">All receipt statuses</option>
                    <option value="PENDING">Receipt pending</option>
                    <option value="RECEIVED">Receipt uploaded</option>
                    <option value="NA">No receipt required</option>
                  </select>
                  <input
                    type="month"
                    value={filterMonth}
                    onChange={(e) => setFilterMonth(e.target.value)}
                    className="bg-slate-950 border border-slate-800 rounded-lg py-2 px-3 text-xs text-white focus:outline-none focus:border-indigo-500"
                  />
                </div>

                {/* Ledger Grid Table */}
                <div className="bg-slate-900 border border-slate-800 rounded-xl overflow-hidden shadow-lg">
                  <div className="overflow-x-auto">
                    <table className="w-full text-left text-xs border-collapse">
                      <thead>
                        <tr className="bg-slate-950 border-b border-slate-800 text-slate-400">
                          <th className="p-3 font-semibold uppercase tracking-wider">Date</th>
                          <th className="p-3 font-semibold uppercase tracking-wider">Tx No</th>
                          <th className="p-3 font-semibold uppercase tracking-wider">Description</th>
                          <th className="p-3 font-semibold uppercase tracking-wider">Paid by</th>
                          <th className="p-3 font-semibold uppercase tracking-wider">Receipt</th>
                          <th className="p-3 font-semibold uppercase tracking-wider">Voucher</th>
                          <th className="p-3 font-semibold uppercase tracking-wider text-right">Amount</th>
                          <th className="p-3 font-semibold uppercase tracking-wider text-right">Actions</th>
                        </tr>
                      </thead>
                      <tbody>
                        {getFilteredTransactions().map((t) => (
                          <tr key={t.id} className="border-b border-slate-900 hover:bg-slate-900/30">
                            <td className="p-3 text-slate-300 font-medium whitespace-nowrap">{t.date}</td>
                            <td className="p-3 text-slate-400 font-mono">{t.transactionNo}</td>
                            <td className="p-3 max-w-[200px] truncate">
                              <div className="font-bold text-slate-200 truncate">{t.description}</div>
                              <div className="text-[10px] text-slate-500 mt-0.5 truncate">
                                {t.type === 'TOPUP' ? 'replenishment' : `${t.categoryName} → ${t.subcategoryName || 'Other'}`}
                                {t.payee ? ` · Payee: ${t.payee}` : ''}
                              </div>
                            </td>
                            <td className="p-3 text-slate-400 font-medium truncate">{t.payer}</td>
                            <td className="p-3">
                              {t.type === 'TOPUP' ? (
                                <span className="bg-slate-950 text-slate-600 px-2 py-0.5 rounded text-[10px] font-bold border border-slate-850">
                                  —
                                </span>
                              ) : t.receiptStatus === 'RECEIVED' ? (
                                <button
                                  onClick={() => handleReceiptBadgeClick(t)}
                                  className="bg-emerald-950 hover:bg-emerald-900 text-emerald-400 px-2 py-0.5 rounded text-[10px] font-bold border border-emerald-900 cursor-pointer flex items-center gap-1 max-w-[120px] truncate"
                                  title={`Download ${t.receiptName}`}
                                >
                                  ✅ {t.receiptName || 'Download'}
                                </button>
                              ) : t.receiptStatus === 'PENDING' ? (
                                <button
                                  onClick={() => handleReceiptBadgeClick(t)}
                                  className="bg-amber-950 hover:bg-amber-900 text-amber-400 px-2 py-0.5 rounded text-[10px] font-bold border border-amber-900 cursor-pointer animate-[pulse_1.5s_infinite]"
                                  title="Click to upload receipt"
                                >
                                  ⏳ Pending
                                </button>
                              ) : (
                                <span className="bg-slate-950 text-slate-500 px-2 py-0.5 rounded text-[10px] font-bold border border-slate-850">
                                  N/A
                                </span>
                              )}
                            </td>
                            <td className="p-3">
                              {t.type === 'EXPENSE' ? (
                                <button
                                  onClick={() => handleDownloadVoucher(t.id, t.transactionNo)}
                                  className={`text-[10px] font-bold px-2 py-0.5 rounded border transition ${
                                    t.voucherFileId
                                      ? 'bg-indigo-950 text-indigo-400 border-indigo-900 hover:bg-indigo-900/30'
                                      : 'bg-slate-950 text-slate-400 border-slate-800 hover:bg-slate-900'
                                  }`}
                                >
                                  🧾 Voucher
                                </button>
                              ) : (
                                <span className="text-slate-600">—</span>
                              )}
                            </td>
                            <td
                              className={`p-3 text-right font-extrabold whitespace-nowrap ${
                                t.type === 'TOPUP' ? 'text-emerald-500' : 'text-red-500'
                              }`}
                            >
                              {t.type === 'TOPUP' ? '+' : '-'}₹
                              {t.amount.toLocaleString('en-IN', { minimumFractionDigits: 2 })}
                            </td>
                            <td className="p-3 text-right">
                              {currentUser.role === 'ADMIN' && t.type === 'EXPENSE' && (
                                <button
                                  onClick={() => handleToggleReceiptStatus(t.id, t.receiptStatus)}
                                  className="text-[10px] text-slate-500 hover:text-indigo-400 border border-slate-800 hover:border-indigo-900 rounded px-2 py-0.5 transition"
                                  title="Toggles receipt validation status"
                                >
                                  Verify Status
                                </button>
                              )}
                            </td>
                          </tr>
                        ))}
                        {getFilteredTransactions().length === 0 && (
                          <tr>
                            <td colSpan={8} className="p-8 text-center text-slate-500 text-xs font-semibold">
                              🗂️ No matching transactions found in database directory.
                            </td>
                          </tr>
                        )}
                      </tbody>
                    </table>
                  </div>
                </div>
              </section>
            )}

            {/* ============ VIEW: CONFIGURATIONS ============ */}
            {activeTab === 'settings' && currentUser.role === 'ADMIN' && (
              <section className="animate-[pop_0.15s_ease]">
                <AdminPanel
                  currentUser={currentUser}
                  categories={categories}
                  templates={templates}
                  lowThreshold={cashbox.lowThreshold}
                  onRefresh={fetchInitialData}
                  toast={showToast}
                />
              </section>
            )}
          </>
        )}
      </main>

      {/* Global alert notifications (toast) */}
      <div
        className={`fixed bottom-6 left-1/2 -translate-x-1/2 bg-slate-900 border border-slate-800 text-white font-bold py-3 px-5 rounded-xl shadow-2xl transition duration-300 z-99 text-xs flex items-center justify-center ${
          toastMessage ? 'translate-y-0 opacity-100' : 'translate-y-12 opacity-0 pointer-events-none'
        }`}
      >
        {toastMessage}
      </div>

      {/* Transaction recording Modal dialog */}
      <TransactionModal
        isOpen={isTxModalOpen}
        onClose={() => setIsTxModalOpen(false)}
        onSuccess={fetchInitialData}
        currentUser={currentUser}
        categories={categories}
        templates={templates}
        toast={showToast}
        defaultType={txModalDefaultType}
      />
    </div>
  );
};
