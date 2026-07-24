import React, { useState, useEffect } from 'react';
import { api } from '../api/client';
import type { TransactionResponse, CategoryResponse } from '../types';

interface EditTransactionModalProps {
  transaction: TransactionResponse | null;
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
  categories: CategoryResponse[];
  toast: (msg: string) => void;
  companies: string[];
}

export const EditTransactionModal: React.FC<EditTransactionModalProps> = ({
  transaction,
  isOpen,
  onClose,
  onSuccess,
  categories,
  toast,
  companies,
}) => {
  const [amount, setAmount] = useState('');
  const [description, setDescription] = useState('');
  const [date, setDate] = useState('');
  const [payee, setPayee] = useState('');
  const [categoryId, setCategoryId] = useState<string>('');
  const [subcategoryId, setSubcategoryId] = useState<string>('');
  const [voucherNumber, setVoucherNumber] = useState('');
  const [company, setCompany] = useState('');
  const [receiptFile, setReceiptFile] = useState<File | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [amountChanged, setAmountChanged] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Populate form whenever transaction changes
  useEffect(() => {
    if (transaction) {
      setAmount(transaction.amount.toString());
      setDescription(transaction.description);
      setDate(transaction.date);
      setPayee(transaction.payee || '');
      setCategoryId(transaction.categoryId?.toString() || '');
      setSubcategoryId(transaction.subcategoryId?.toString() || '');
      setVoucherNumber(transaction.voucherNumber || '');
      setCompany(transaction.company);
      setReceiptFile(null);
      setAmountChanged(false);
      setError(null);
    }
  }, [transaction]);

  if (!isOpen || !transaction) return null;

  const selectedCategory = categories.find((c) => c.id === Number(categoryId));
  const subcategories = selectedCategory?.subcategories || [];
  const isExpense = transaction.type === 'EXPENSE';

  const originalAmount = transaction.amount;
  const parsedNewAmount = parseFloat(amount);
  const amountDiff = isNaN(parsedNewAmount) ? 0 : parsedNewAmount - originalAmount;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!transaction) return;
    setError(null);

    const parsedAmount = parseFloat(amount);
    if (isNaN(parsedAmount) || parsedAmount <= 0) {
      setError('Amount must be a positive number');
      return;
    }

    setSubmitting(true);
    try {
      const formData = new FormData();
      const requestPayload = {
        amount: parsedAmount,
        description: description.trim(),
        date,
        payee: payee.trim() || undefined,
        categoryId: isExpense && categoryId ? Number(categoryId) : null,
        subcategoryId: subcategoryId ? Number(subcategoryId) : null,
        voucherNumber: voucherNumber.trim() || undefined,
        company: company.trim(),
      };

      formData.append(
        'request',
        new Blob([JSON.stringify(requestPayload)], { type: 'application/json' })
      );

      if (receiptFile) {
        formData.append('file', receiptFile);
      }

      await api.transactions.update(transaction.id, formData);
      toast('✅ Transaction updated successfully!');
      onSuccess();
      onClose();
    } catch (err) {
      setError('Failed to update: ' + (err instanceof Error ? err.message : String(err)));
    } finally {
      setSubmitting(false);
    }
  };

  const handleBackdropClick = (e: React.MouseEvent<HTMLDivElement>) => {
    if (e.target === e.currentTarget) onClose();
  };

  return (
    <div
      className="fixed inset-0 bg-slate-950/80 backdrop-blur-md z-[100] flex items-center justify-center p-4"
      onClick={handleBackdropClick}
    >
      <div className="bg-slate-900 border border-slate-800 rounded-2xl w-full max-w-2xl shadow-2xl flex flex-col overflow-hidden max-h-[92vh] animate-[pop_0.15s_ease]">
        {/* Header */}
        <div className="flex items-center justify-between p-5 border-b border-slate-800 bg-slate-950/50 shrink-0">
          <div className="flex flex-col gap-1">
            <div className="flex items-center gap-2">
              <span className="text-base">✏️</span>
              <h2 className="text-sm font-extrabold text-white">Edit Transaction</h2>
              <span
                className={`px-2 py-0.5 rounded text-[10px] font-bold ${
                  isExpense
                    ? 'bg-red-950/60 text-red-400 border border-red-900/40'
                    : 'bg-emerald-950/60 text-emerald-400 border border-emerald-900/40'
                }`}
              >
                {isExpense ? '↑ EXPENSE' : '↓ TOPUP'}
              </span>
            </div>
            <p className="text-[10px] text-slate-500 font-mono">{transaction.transactionNo}</p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="w-8 h-8 rounded-full bg-slate-800 hover:bg-slate-700 text-slate-400 hover:text-white flex items-center justify-center transition font-bold text-xs"
          >
            ✕
          </button>
        </div>

        {error && (
          <div className="mx-5 mt-4 p-3 bg-red-950/40 border border-red-900/60 rounded-lg text-red-200 text-xs font-semibold flex items-center justify-between gap-2 animate-[shake_0.2s_ease-in-out]">
            <div className="flex items-center gap-2">
              <span className="text-sm">⚠️</span>
              <span className="flex-1">{error}</span>
            </div>
            <button
              type="button"
              onClick={() => setError(null)}
              className="text-red-400 hover:text-red-300 font-bold px-1.5 py-0.5 rounded cursor-pointer"
            >
              ✕
            </button>
          </div>
        )}

        {/* Amount change warning banner */}
        {amountChanged && amountDiff !== 0 && (
          <div
            className={`px-5 py-2.5 text-xs font-semibold flex items-center gap-2 border-b ${
              amountDiff > 0
                ? isExpense
                  ? 'bg-red-950/40 text-red-300 border-red-900/40'
                  : 'bg-emerald-950/40 text-emerald-300 border-emerald-900/40'
                : isExpense
                ? 'bg-emerald-950/40 text-emerald-300 border-emerald-900/40'
                : 'bg-amber-950/40 text-amber-300 border-amber-900/40'
            }`}
          >
            <span>⚠️</span>
            <span>
              Amount changed by{' '}
              <strong>
                {amountDiff > 0 ? '+' : ''}₹{Math.abs(amountDiff).toLocaleString('en-IN', { minimumFractionDigits: 2 })}
              </strong>
              {' '}— cashbox balance will be adjusted automatically.
            </span>
          </div>
        )}

        {/* Form */}
        <form onSubmit={handleSubmit} className="flex-1 overflow-y-auto">
          <div className="p-5 flex flex-col gap-4">
            {/* Amount + Date row */}
            <div className="grid grid-cols-2 gap-4">
              <div className="flex flex-col gap-1.5">
                <label className="text-[10px] font-bold text-slate-500 uppercase tracking-widest">
                  Amount (₹) <span className="text-red-500">*</span>
                </label>
                <input
                  type="number"
                  step="0.01"
                  min="0.01"
                  value={amount}
                  onChange={(e) => {
                    setAmount(e.target.value);
                    setAmountChanged(true);
                  }}
                  required
                  className="bg-slate-950 border border-slate-800 focus:border-indigo-500 rounded-lg py-2.5 px-3 text-sm font-bold text-white focus:outline-none transition"
                  placeholder="0.00"
                />
                <span className="text-[10px] text-slate-600">
                  Original: ₹{originalAmount.toLocaleString('en-IN', { minimumFractionDigits: 2 })}
                </span>
              </div>
              <div className="flex flex-col gap-1.5">
                <label className="text-[10px] font-bold text-slate-500 uppercase tracking-widest">
                  Date <span className="text-red-500">*</span>
                </label>
                <div className="relative flex items-center">
                  <input
                    type="date"
                    value={date}
                    onChange={(e) => setDate(e.target.value)}
                    onClick={(e) => { try { (e.currentTarget as HTMLInputElement).showPicker(); } catch {} }}
                    required
                    className="w-full bg-slate-950 border border-slate-800 focus:border-indigo-500 rounded-lg py-2.5 pl-3 pr-10 text-sm text-white focus:outline-none transition cursor-pointer hide-native-datepicker"
                  />
                  <svg className="w-4 h-4 text-indigo-400 absolute right-3 pointer-events-none" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
                  </svg>
                </div>
              </div>
            </div>

            {/* Description */}
            <div className="flex flex-col gap-1.5">
              <label className="text-[10px] font-bold text-slate-500 uppercase tracking-widest">
                Description <span className="text-red-500">*</span>
              </label>
              <input
                type="text"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                required
                maxLength={255}
                className="bg-slate-950 border border-slate-800 focus:border-indigo-500 rounded-lg py-2.5 px-3 text-sm text-white focus:outline-none transition"
                placeholder="Transaction description..."
              />
            </div>

            {/* Payee */}
            <div className="flex flex-col gap-1.5">
              <label className="text-[10px] font-bold text-slate-500 uppercase tracking-widest">
                Payee / Recipient
              </label>
              <input
                type="text"
                value={payee}
                onChange={(e) => setPayee(e.target.value)}
                maxLength={255}
                className="bg-slate-950 border border-slate-800 focus:border-indigo-500 rounded-lg py-2.5 px-3 text-sm text-white focus:outline-none transition"
                placeholder="Recipient name (optional)"
              />
            </div>

            {/* Category + Subcategory (only for expenses) */}
            {isExpense && (
              <div className="grid grid-cols-2 gap-4">
                <div className="flex flex-col gap-1.5">
                  <label className="text-[10px] font-bold text-slate-500 uppercase tracking-widest">
                    Category <span className="text-red-500">*</span>
                  </label>
                  <select
                    value={categoryId}
                    onChange={(e) => {
                      setCategoryId(e.target.value);
                      setSubcategoryId('');
                    }}
                    required
                    className="bg-slate-950 border border-slate-800 focus:border-indigo-500 rounded-lg py-2.5 px-3 text-sm text-white focus:outline-none transition"
                  >
                    <option value="">Select category</option>
                    {categories.map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.name}
                      </option>
                    ))}
                  </select>
                </div>
                <div className="flex flex-col gap-1.5">
                  <label className="text-[10px] font-bold text-slate-500 uppercase tracking-widest">
                    Subcategory
                  </label>
                  <select
                    value={subcategoryId}
                    onChange={(e) => setSubcategoryId(e.target.value)}
                    disabled={subcategories.length === 0}
                    className="bg-slate-950 border border-slate-800 focus:border-indigo-500 rounded-lg py-2.5 px-3 text-sm text-white focus:outline-none transition disabled:opacity-50"
                  >
                    <option value="">None / Other</option>
                    {subcategories.map((sc) => (
                      <option key={sc.id} value={sc.id}>
                        {sc.name}
                      </option>
                    ))}
                  </select>
                </div>
              </div>
            )}

            {/* Voucher + Company row */}
            <div className="grid grid-cols-2 gap-4">
              <div className="flex flex-col gap-1.5">
                <label className="text-[10px] font-bold text-slate-500 uppercase tracking-widest">
                  Voucher No. <span className="text-slate-600 normal-case font-normal">(Optional)</span>
                </label>
                <input
                  type="text"
                  value={voucherNumber}
                  onChange={(e) => setVoucherNumber(e.target.value)}
                  className="bg-slate-950 border border-slate-800 focus:border-indigo-500 rounded-lg py-2.5 px-3 text-sm text-white focus:outline-none transition font-mono"
                  placeholder="e.g. VCH-001 — can be added later"
                />
              </div>
              <div className="flex flex-col gap-1.5">
                <label className="text-[10px] font-bold text-slate-500 uppercase tracking-widest">
                  Company <span className="text-red-500">*</span>
                </label>
                <select
                  value={company}
                  onChange={(e) => setCompany(e.target.value)}
                  required
                  className="bg-slate-950 border border-slate-800 focus:border-indigo-500 rounded-lg py-2.5 px-3 text-sm text-white focus:outline-none transition"
                >
                  <option value="">Select company</option>
                  {companies.map((c) => (
                    <option key={c} value={c}>
                      {c}
                    </option>
                  ))}
                </select>
              </div>
            </div>

            {/* Receipt Upload */}
            {isExpense && (
              <div className="flex flex-col gap-1.5">
                <label className="text-[10px] font-bold text-slate-500 uppercase tracking-widest">
                  Upload / Replace Receipt <span className="text-slate-600 normal-case font-normal">(Optional)</span>
                </label>
                {transaction.receiptName && !receiptFile && (
                  <p className="text-[10px] text-slate-400">
                    Current: <span className="text-indigo-400 font-semibold">{transaction.receiptName}</span>
                  </p>
                )}
                <div className="relative">
                  <input
                    type="file"
                    onChange={(e) => setReceiptFile(e.target.files?.[0] || null)}
                    className="w-full bg-slate-950 border border-dashed border-slate-700 hover:border-indigo-500 rounded-lg py-2.5 px-3 text-sm text-slate-400 focus:outline-none transition cursor-pointer file:bg-indigo-600 file:text-white file:border-0 file:rounded-md file:px-3 file:py-1 file:text-xs file:font-bold file:mr-3 file:cursor-pointer"
                  />
                </div>
                {receiptFile && (
                  <div className="flex items-center gap-2 text-[10px] text-emerald-400">
                    <span>📎</span>
                    <span className="font-semibold">{receiptFile.name}</span>
                    <button
                      type="button"
                      onClick={() => setReceiptFile(null)}
                      className="text-red-400 hover:text-red-300 font-bold px-1 cursor-pointer"
                    >
                      ✕
                    </button>
                  </div>
                )}
              </div>
            )}
          </div>

          {/* Footer */}
          <div className="p-5 border-t border-slate-800 bg-slate-950/30 flex justify-end gap-3 shrink-0">
            <button
              type="button"
              onClick={onClose}
              disabled={submitting}
              className="py-2.5 px-5 bg-slate-800 hover:bg-slate-700 border border-slate-700 text-slate-300 hover:text-white rounded-lg text-xs font-bold transition disabled:opacity-50"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="py-2.5 px-6 bg-indigo-600 hover:bg-indigo-500 text-white rounded-lg text-xs font-bold shadow-lg shadow-indigo-600/20 hover:shadow-indigo-500/30 transition disabled:opacity-60 flex items-center gap-2"
            >
              {submitting ? (
                <>
                  <span className="w-3.5 h-3.5 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                  Saving...
                </>
              ) : (
                '💾 Save Changes'
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
