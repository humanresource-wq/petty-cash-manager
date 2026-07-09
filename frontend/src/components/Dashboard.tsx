import React, { useState, useEffect } from 'react';
import { api } from '../api/client';
import type {
  TransactionResponse,
  CategoryResponse,
  ExpenseTemplate,
  UserResponse,
  CashBoxResponse,
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
  const formatDateTime = (timestampStr: string | null) => {
    if (!timestampStr) return '—';
    try {
      const date = new Date(timestampStr);
      if (isNaN(date.getTime())) return timestampStr;
      
      const day = String(date.getDate()).padStart(2, '0');
      const month = String(date.getMonth() + 1).padStart(2, '0');
      const year = date.getFullYear();
      
      let hours = date.getHours();
      const minutes = String(date.getMinutes()).padStart(2, '0');
      const seconds = String(date.getSeconds()).padStart(2, '0');
      const ampm = hours >= 12 ? 'PM' : 'AM';
      hours = hours % 12;
      hours = hours ? hours : 12;
      const hoursStr = String(hours).padStart(2, '0');
      
      return `${day}-${month}-${year} ${hoursStr}:${minutes}:${seconds} ${ampm}`;
    } catch (e) {
      return timestampStr;
    }
  };

  const [activeTab, setActiveTab] = useState<'dashboard' | 'transactions' | 'settings'>('dashboard');
  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const [toastTimeoutId, setToastTimeoutId] = useState<any>(null);

  // Core Data States
  const [transactions, setTransactions] = useState<TransactionResponse[]>([]);
  const [totalElements, setTotalElements] = useState<number>(0);
  const [totalPages, setTotalPages] = useState<number>(0);
  const [categories, setCategories] = useState<CategoryResponse[]>([]);
  const [templates, setTemplates] = useState<ExpenseTemplate[]>([]);
  const [cashbox, setCashbox] = useState<CashBoxResponse>({ balance: 0, lowThreshold: 2000 });
  const [dashboardStats, setDashboardStats] = useState<DashboardStatsResponse | null>(null);
  const [loading, setLoading] = useState<boolean>(true);

  // Filters for Transactions Tab
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [filterType, setFilterType] = useState<string>('');
  const [filterCategory, setFilterCategory] = useState<string>('');
  const [filterStartDate, setFilterStartDate] = useState<string>('');
  const [filterEndDate, setFilterEndDate] = useState<string>('');

  // Pagination for Transactions Ledger
  const [currentPage, setCurrentPage] = useState<number>(1);
  const pageSize = 10;

  // Reset pagination to page 1 whenever filters change
  useEffect(() => {
    setCurrentPage(1);
  }, [searchQuery, filterType, filterCategory, filterStartDate, filterEndDate]);

  // Modals
  const [isTxModalOpen, setIsTxModalOpen] = useState<boolean>(false);
  const [txModalDefaultType, setTxModalDefaultType] = useState<'EXPENSE' | 'TOPUP'>('EXPENSE');

  // Previews
  const [previewTx, setPreviewTx] = useState<TransactionResponse | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [previewLoading, setPreviewLoading] = useState<boolean>(false);

  const fetchTransactions = async () => {
    try {
      const pageData = await api.transactions.list({
        page: currentPage - 1,
        size: pageSize,
        startDate: filterStartDate || undefined,
        endDate: filterEndDate || undefined,
        type: filterType || undefined,
        categoryName: filterCategory || undefined,
        search: searchQuery || undefined,
      });
      setTransactions(pageData.content);
      setTotalElements(pageData.totalElements);
      setTotalPages(pageData.totalPages);
    } catch (err) {
      showToast('❌ Failed to fetch transactions: ' + (err instanceof Error ? err.message : String(err)));
    }
  };

  const fetchInitialData = async () => {
    setLoading(true);
    try {
      const [catData, tempData, statsData] = await Promise.all([
        api.categories.list(),
        api.templates.list(),
        api.transactions.getDashboardStats(),
      ]);
      setCategories(catData);
      setTemplates(tempData);
      setDashboardStats(statsData);
      setCashbox({ balance: statsData.balance, lowThreshold: statsData.lowThreshold });
      await fetchTransactions();
    } catch (err) {
      showToast('❌ Failed to fetch database state: ' + (err instanceof Error ? err.message : String(err)));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchInitialData();
  }, []);

  const isFirstMount = React.useRef(true);
  useEffect(() => {
    if (isFirstMount.current) {
      isFirstMount.current = false;
      return;
    }
    const fetchTxs = async () => {
      setLoading(true);
      await fetchTransactions();
      setLoading(false);
    };
    fetchTxs();
  }, [currentPage, searchQuery, filterType, filterCategory, filterStartDate, filterEndDate]);

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





  const handleViewReceipt = async (tx: TransactionResponse) => {
    if (!tx.receiptName) return;
    setPreviewTx(tx);
    setPreviewLoading(true);
    try {
      const blob = await api.transactions.downloadReceipt(tx.id);
      const isPdf = tx.receiptName.toLowerCase().endsWith('.pdf');
      const mimeType = isPdf ? 'application/pdf' : 'image/png';
      const typedBlob = new Blob([blob], { type: mimeType });
      const url = URL.createObjectURL(typedBlob);
      setPreviewUrl(url);
    } catch (err) {
      showToast('❌ Failed to load receipt preview: ' + (err instanceof Error ? err.message : String(err)));
      setPreviewTx(null);
    } finally {
      setPreviewLoading(false);
    }
  };

  const handleClosePreview = () => {
    if (previewUrl) {
      URL.revokeObjectURL(previewUrl);
    }
    setPreviewUrl(null);
    setPreviewTx(null);
  };

  const handleDownloadInPreview = () => {
    if (previewTx) {
      handleDownloadReceipt(previewTx.id, previewTx.receiptName || 'receipt');
    }
  };

  const handleExport = async (format: 'csv' | 'pdf') => {
    showToast(`📥 Preparing statement export as ${format.toUpperCase()}...`);
    try {
      const params = {
        startDate: filterStartDate || undefined,
        endDate: filterEndDate || undefined,
        type: filterType || undefined,
        categoryName: filterCategory || undefined,
        search: searchQuery || undefined,
      };

      let blobData;
      let filename;
      const today = new Date().toISOString().split('T')[0];

      if (format === 'csv') {
        blobData = await api.transactions.exportCsv(params);
        filename = `transactions-report-${today}.csv`;
      } else {
        blobData = await api.transactions.exportPdf(params);
        filename = `ledger-summary-${today}.pdf`;
      }

      const url = URL.createObjectURL(new Blob([blobData]));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', filename);
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);

      showToast(`✅ Exported successfully! Check your downloads.`);
    } catch (err) {
      showToast('❌ Export failed: ' + (err instanceof Error ? err.message : String(err)));
    }
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

  const hasActiveFilters = !!(
    searchQuery ||
    filterType ||
    filterCategory ||
    filterStartDate ||
    filterEndDate
  );

  const handleClearFilters = () => {
    setSearchQuery('');
    setFilterType('');
    setFilterCategory('');
    setFilterStartDate('');
    setFilterEndDate('');
  };

  // --- Filtered Transactions list ---
  const getFilteredTransactions = () => {
    return transactions;
  };

  const getPaginatedTransactions = () => {
    return transactions;
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
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
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
                      Every rupee in and out — view ledger history and download attachments.
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

                  <div className="flex items-center gap-1.5 bg-slate-950 border border-slate-800 rounded-lg px-2.5 py-1.5">
                    <span className="text-[10px] text-slate-500 font-bold uppercase select-none">From</span>
                    <input
                      type="date"
                      value={filterStartDate}
                      onChange={(e) => setFilterStartDate(e.target.value)}
                      onClick={(e) => {
                        try { e.currentTarget.showPicker(); } catch {}
                      }}
                      className="bg-transparent text-xs text-white focus:outline-none cursor-pointer"
                    />
                    <span className="text-[10px] text-slate-500 font-bold uppercase select-none">To</span>
                    <input
                      type="date"
                      value={filterEndDate}
                      onChange={(e) => setFilterEndDate(e.target.value)}
                      onClick={(e) => {
                        try { e.currentTarget.showPicker(); } catch {}
                      }}
                      className="bg-transparent text-xs text-white focus:outline-none cursor-pointer"
                    />
                  </div>

                  {hasActiveFilters && (
                    <button
                      type="button"
                      onClick={handleClearFilters}
                      className="bg-red-950/40 hover:bg-red-950/80 text-red-400 hover:text-red-300 font-bold text-xs py-2 px-3 border border-red-900/30 hover:border-red-900/60 rounded-lg transition active:scale-[0.98] cursor-pointer"
                      title="Clear all active ledger filters"
                    >
                      🧹 Clear Filters
                    </button>
                  )}

                  {/* Export Buttons */}
                  <div className="flex gap-2 shrink-0 ml-auto">
                    <button
                      type="button"
                      onClick={() => handleExport('csv')}
                      className="bg-slate-950 border border-slate-800 hover:border-slate-700 text-slate-350 hover:text-white font-bold text-xs py-2 px-3 rounded-lg flex items-center gap-1.5 transition active:scale-[0.98] cursor-pointer"
                      title="Download spreadsheet CSV"
                    >
                      📊 Export CSV
                    </button>
                    <button
                      type="button"
                      onClick={() => handleExport('pdf')}
                      className="bg-slate-950 border border-slate-800 hover:border-slate-700 text-slate-350 hover:text-white font-bold text-xs py-2 px-3 rounded-lg flex items-center gap-1.5 transition active:scale-[0.98] cursor-pointer"
                      title="Download summary statement PDF"
                    >
                      📄 Export PDF Summary
                    </button>
                  </div>
                </div>

                {/* Ledger Grid Table */}
                <div className="bg-slate-900 border border-slate-800 rounded-xl overflow-hidden shadow-lg">
                  <div className="overflow-x-auto">
                    <table className="w-full text-left text-xs border-collapse">
                      <thead>
                        <tr className="bg-slate-950 border-b border-slate-800 text-slate-400">
                          <th className="p-3 font-semibold uppercase tracking-wider">Date & Time</th>
                          <th className="p-3 font-semibold uppercase tracking-wider">Tx No</th>
                          <th className="p-3 font-semibold uppercase tracking-wider">Description</th>
                          <th className="p-3 font-semibold uppercase tracking-wider">Payee</th>
                          <th className="p-3 font-semibold uppercase tracking-wider">Paid by</th>
                          <th className="p-3 font-semibold uppercase tracking-wider">Receipt</th>
                          <th className="p-3 font-semibold uppercase tracking-wider">Voucher</th>
                          <th className="p-3 font-semibold uppercase tracking-wider text-right">Amount</th>
                        </tr>
                      </thead>
                      <tbody>
                        {getPaginatedTransactions().map((t) => (
                          <tr key={t.id} className="border-b border-slate-900 hover:bg-slate-900/30">
                            <td className="p-3 text-slate-300 font-medium whitespace-nowrap">{formatDateTime(t.timestamp)}</td>
                            <td className="p-3 text-slate-400 font-mono">{t.transactionNo}</td>
                            <td className="p-3 max-w-[200px] truncate">
                              <div className="font-bold text-slate-200 truncate">{t.description}</div>
                              <div className="text-[10px] text-slate-500 mt-0.5 truncate">
                                {t.type === 'TOPUP' ? 'replenishment' : `${t.categoryName} → ${t.subcategoryName || 'Other'}`}
                              </div>
                            </td>
                            <td className="p-3 text-slate-300 font-medium truncate">{t.payee || '—'}</td>
                            <td className="p-3 text-slate-400 font-medium truncate">{t.payer}</td>
                            <td className="p-3">
                              {t.receiptName ? (
                                <button
                                  onClick={() => handleViewReceipt(t)}
                                  className="bg-emerald-950 hover:bg-emerald-900 text-emerald-400 px-2 py-0.5 rounded text-[10px] font-bold border border-emerald-900 cursor-pointer flex items-center gap-1 max-w-[120px] truncate"
                                  title={`Download ${t.receiptName}`}
                                >
                                  ✅ {t.receiptName}
                                </button>
                              ) : null}
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

                  {/* Pagination Footer */}
                  {totalElements > 0 && (
                    <div className="flex items-center justify-between p-4 border-t border-slate-800/60 bg-slate-950/20 text-xs text-slate-400">
                      <div>
                        Showing <span className="font-bold text-slate-200">{totalElements === 0 ? 0 : (currentPage - 1) * pageSize + 1}</span> to{' '}
                        <span className="font-bold text-slate-200">
                          {Math.min(totalElements, currentPage * pageSize)}
                        </span>{' '}
                        of <span className="font-bold text-slate-200">{totalElements}</span> entries
                      </div>
                      
                      <div className="flex items-center gap-1.5">
                        <button
                          type="button"
                          onClick={() => setCurrentPage(prev => Math.max(1, prev - 1))}
                          disabled={currentPage === 1}
                          className="px-2.5 py-1 rounded bg-slate-950 border border-slate-800 text-[10px] font-bold text-slate-300 hover:text-white hover:bg-slate-900 transition disabled:opacity-40 disabled:cursor-not-allowed select-none"
                        >
                          Previous
                        </button>
                        
                        {Array.from({ length: totalPages }).map((_, idx) => {
                          const pageNum = idx + 1;
                          return (
                            <button
                              key={pageNum}
                              type="button"
                              onClick={() => setCurrentPage(pageNum)}
                              className={`w-6 h-6 rounded text-[10px] font-bold transition select-none flex items-center justify-center ${
                                currentPage === pageNum
                                  ? 'bg-indigo-600 text-white shadow-md'
                                  : 'bg-slate-950 border border-slate-800 text-slate-400 hover:text-white hover:bg-slate-900'
                              }`}
                            >
                              {pageNum}
                            </button>
                          );
                        })}
                        
                        <button
                          type="button"
                          onClick={() => setCurrentPage(prev => Math.min(totalPages, prev + 1))}
                          disabled={currentPage === totalPages || totalPages === 0}
                          className="px-2.5 py-1 rounded bg-slate-950 border border-slate-800 text-[10px] font-bold text-slate-300 hover:text-white hover:bg-slate-900 transition disabled:opacity-40 disabled:cursor-not-allowed select-none"
                        >
                          Next
                        </button>
                      </div>
                    </div>
                  )}
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

      {/* Inline Receipt Previewer Modal */}
      {previewTx && (
        <div className="fixed inset-0 bg-slate-950/80 backdrop-blur-md z-99 flex items-center justify-center p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl w-full max-w-4xl shadow-2xl flex flex-col overflow-hidden max-h-[90vh]">
            {/* Modal Header */}
            <div className="flex items-center justify-between p-4 border-b border-slate-800 bg-slate-950/50">
              <div className="flex flex-col gap-1 min-w-0">
                <span className="text-[10px] font-bold text-slate-500 uppercase tracking-widest">
                  Auditing Staged Receipt · {previewTx.transactionNo}
                </span>
                <h3 className="text-sm font-extrabold text-white truncate max-w-[400px]">
                  {previewTx.receiptName || 'Attached Invoice Document'}
                </h3>
              </div>
              <div className="flex items-center gap-3">
                <button
                  type="button"
                  onClick={handleDownloadInPreview}
                  className="py-1.5 px-3 bg-slate-900 hover:bg-slate-800 border border-slate-800 hover:border-slate-700 text-slate-350 hover:text-white rounded-lg text-[10px] font-bold transition flex items-center gap-1.5"
                  title="Save copy to local disk"
                >
                  📥 Download Copy
                </button>
                <button
                  type="button"
                  onClick={handleClosePreview}
                  className="w-8 h-8 rounded-full bg-slate-950 hover:bg-slate-850 text-slate-400 hover:text-white flex items-center justify-center transition font-bold"
                  title="Close Dialog"
                >
                  ✕
                </button>
              </div>
            </div>

            {/* Modal Body */}
            <div className="p-6 flex-1 flex flex-col items-center justify-center overflow-auto min-h-[40vh] bg-slate-950/20">
              {previewLoading ? (
                <div className="flex flex-col items-center gap-3">
                  <span className="w-8 h-8 border-4 border-indigo-500/30 border-t-indigo-500 rounded-full animate-spin"></span>
                  <span className="text-xs text-slate-500 font-semibold">Fetching document from Google Drive...</span>
                </div>
              ) : previewUrl ? (
                previewTx.receiptName?.toLowerCase().endsWith('.pdf') ? (
                  <iframe
                    src={previewUrl}
                    title="Inline PDF Document Viewer"
                    className="w-full h-[60vh] rounded-lg border border-slate-800 bg-slate-950"
                  />
                ) : (
                  <div className="w-full h-[60vh] flex items-center justify-center p-2">
                    <img
                      src={previewUrl}
                      alt="Inline Receipt View"
                      className="max-w-full max-h-full object-contain rounded-lg border border-slate-800 bg-slate-950 shadow-inner"
                    />
                  </div>
                )
              ) : (
                <div className="text-center text-slate-500">
                  <div className="text-3xl mb-2">⚠️</div>
                  <div className="text-xs font-semibold">Failed to load content preview for this file format.</div>
                </div>
              )}
            </div>

            {/* Modal Footer */}
            <div className="p-4 border-t border-slate-800 bg-slate-950/50 flex justify-end gap-3">
              <button
                type="button"
                onClick={handleClosePreview}
                className="py-2 px-5 bg-indigo-600 hover:bg-indigo-500 text-white rounded-lg text-xs font-bold shadow-lg transition"
              >
                Close Auditing View
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
