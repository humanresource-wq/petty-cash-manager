import React, { useState, useEffect } from 'react';
import { api } from '../api/client';
import type { CategoryResponse, ExpenseTemplate, UserResponse } from '../types';

interface TransactionModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
  currentUser: UserResponse;
  categories: CategoryResponse[];
  templates: ExpenseTemplate[];
  toast: (msg: string) => void;
  defaultType?: 'EXPENSE' | 'TOPUP';
  companies: string[];
}

export const TransactionModal: React.FC<TransactionModalProps> = ({
  isOpen,
  onClose,
  onSuccess,
  currentUser,
  categories,
  templates,
  toast,
  defaultType,
  companies,
}) => {
  const [txType, setTxType] = useState<'EXPENSE' | 'TOPUP'>('EXPENSE');
  const [amount, setAmount] = useState<string>('');
  const [date, setDate] = useState<string>('');
  const [description, setDescription] = useState<string>('');
  const [payee, setPayee] = useState<string>('');
  const [selectedCategoryId, setSelectedCategoryId] = useState<number | ''>('');
  const [selectedSubcategoryId, setSelectedSubcategoryId] = useState<number | ''>('');
  const [selectedTemplateId, setSelectedTemplateId] = useState<number | ''>('');
  const [voucherNumber, setVoucherNumber] = useState<string>('');
  const [company, setCompany] = useState<string>('');
  const [file, setFile] = useState<File[]>([]);
  const [filePreviewUrls, setFilePreviewUrls] = useState<string[]>([]);
  const [loading, setLoading] = useState<boolean>(false);
  const [dragOver, setDragOver] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  // Preview URL generator for selected files — revokes old URLs on change
  useEffect(() => {
    // Revoke previous URLs
    filePreviewUrls.forEach((u) => URL.revokeObjectURL(u));

    if (file.length === 0) {
      setFilePreviewUrls([]);
      return;
    }

    const urls = file.map((f) =>
      f.type.startsWith('image/') || f.type === 'application/pdf'
        ? URL.createObjectURL(f)
        : ''
    );
    setFilePreviewUrls(urls);

    return () => {
      urls.forEach((u) => { if (u) URL.revokeObjectURL(u); });
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [file]);

  // Default to today's date on open
  useEffect(() => {
    if (isOpen) {
      const today = new Date();
      const localDate = today.getFullYear() + '-' + 
        String(today.getMonth() + 1).padStart(2, '0') + '-' + 
        String(today.getDate()).padStart(2, '0');
      setDate(localDate);

      // Reset fields
      setAmount('');
      setDescription('');
      setPayee('');
      setSelectedCategoryId('');
      setSelectedSubcategoryId('');
      setSelectedTemplateId('');
      setVoucherNumber('');
      setCompany(companies[0] || '');
      setFile([]);
      setError(null);

      // Force to EXPENSE if user is standard USER, otherwise defaultType
      if (currentUser.role === 'USER') {
        setTxType('EXPENSE');
      } else {
        const resolvedType = defaultType || 'EXPENSE';
        setTxType(resolvedType);
        // Clear voucher number when switching to TOPUP
        if (resolvedType === 'TOPUP') setVoucherNumber('');
      }
    }
  }, [isOpen, currentUser, defaultType]);

  if (!isOpen) return null;

  // Selected Category Object to list Subcategories
  const selectedCategory = categories.find((c) => c.id === Number(selectedCategoryId));

  const handleTemplateChange = (templateIdStr: string) => {
    setSelectedTemplateId(templateIdStr ? Number(templateIdStr) : '');
    if (!templateIdStr) return;

    const template = templates.find((t) => t.id === Number(templateIdStr));
    if (template) {
      setAmount(template.amount.toString());
      setDescription(template.name + ': ' + (template.description || ''));
      
      // Auto-select Category by Name matching
      const matchedCat = categories.find((c) => c.name.toLowerCase() === template.category.toLowerCase());
      if (matchedCat) {
        setSelectedCategoryId(matchedCat.id);
        setSelectedSubcategoryId(''); // reset subcategory on template change
      }
      toast(`📝 Auto-populated template: ${template.name}`);
    }
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files.length > 0) {
      const incoming = Array.from(e.target.files);
      setFile((prev) => {
        // Deduplicate by name+size to avoid exact duplicates on re-select
        const existing = new Set(prev.map((f) => f.name + f.size));
        const fresh = incoming.filter((f) => !existing.has(f.name + f.size));
        return [...prev, ...fresh];
      });
      // Reset input value so the same file(s) can be re-selected after removal
      e.target.value = '';
    }
  };

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault();
    setDragOver(true);
  };

  const handleDragLeave = () => {
    setDragOver(false);
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setDragOver(false);
    if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      const incoming = Array.from(e.dataTransfer.files);
      setFile((prev) => {
        const existing = new Set(prev.map((f) => f.name + f.size));
        const fresh = incoming.filter((f) => !existing.has(f.name + f.size));
        return [...prev, ...fresh];
      });
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    const numAmount = parseFloat(amount);
    if (isNaN(numAmount) || numAmount <= 0) {
      setError('Enter a valid positive amount.');
      return;
    }
    if (!date) {
      setError('Select a valid transaction date.');
      return;
    }
    if (!company) {
      setError('Select a company.');
      return;
    }
    if (!description.trim()) {
      setError('Description must not be blank.');
      return;
    }

    if (txType === 'EXPENSE') {
      if (!selectedCategoryId) {
        setError('Select an expense Category.');
        return;
      }
    }

    setLoading(true);
    try {
      const formData = new FormData();
      const requestPayload = {
        type: txType,
        amount: numAmount,
        description: description.trim(),
        date,
        payee: txType === 'EXPENSE' ? payee.trim() : null,
        categoryId: txType === 'EXPENSE' ? Number(selectedCategoryId) : null,
        subcategoryId: txType === 'EXPENSE' && selectedSubcategoryId ? Number(selectedSubcategoryId) : null,
        voucherNumber: voucherNumber.trim() || undefined,
        company,
      };

      formData.append(
        'request',
        new Blob([JSON.stringify(requestPayload)], { type: 'application/json' })
      );

      if (file.length > 0) {
        // Backend currently accepts one file; send the first. For multi-file,
        // append each and the backend will receive the last one (or loop if API is extended).
        file.forEach((f) => formData.append('file', f));
      }

      await api.transactions.record(formData);
      toast(txType === 'EXPENSE' ? '📉 Expense recorded successfully!' : '💵 Cash added to box!');
      onSuccess();
      onClose();
    } catch (err) {
      setError('Failed to record: ' + (err instanceof Error ? err.message : String(err)));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 bg-slate-950/60 backdrop-blur-xs flex items-center justify-center p-4 z-50 overflow-y-auto">
      <div className="relative w-full max-w-lg bg-slate-900 border border-slate-800 rounded-xl shadow-2xl p-6 overflow-hidden animate-[pop_0.18s_ease]">
        <h3 className="text-lg font-bold text-white mb-6">
          {txType === 'EXPENSE' ? 'Record Expense' : 'Add Cash to Box (Top-up)'}
        </h3>

        {error && (
          <div className="mb-6 p-3 bg-red-950/40 border border-red-900/60 rounded-lg text-red-200 text-xs font-semibold flex items-center justify-between gap-2 animate-[shake_0.2s_ease-in-out]">
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

        {/* Tab Selector Segment Button (Admin only) */}
        {currentUser.role === 'ADMIN' && (
          <div className="flex bg-slate-950 p-1 rounded-lg border border-slate-800 gap-1 mb-6">
            <button
              onClick={() => setTxType('EXPENSE')}
              className={`flex-1 py-1.5 rounded-md text-xs font-bold transition ${
                txType === 'EXPENSE' ? 'bg-indigo-600 text-white shadow' : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              📉 Expense
            </button>
            <button
              onClick={() => setTxType('TOPUP')}
              className={`flex-1 py-1.5 rounded-md text-xs font-bold transition ${
                txType === 'TOPUP' ? 'bg-emerald-600 text-white shadow' : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              💵 Cash Top-up
            </button>
          </div>
        )}

        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          {/* Quick Template Picker (Only for Expense) */}
          {txType === 'EXPENSE' && templates.length > 0 && (
            <div className="flex flex-col gap-1">
              <label className="text-xs font-semibold text-slate-400 uppercase tracking-wider">
                Autofill from Expense Template
              </label>
              <select
                value={selectedTemplateId}
                onChange={(e) => handleTemplateChange(e.target.value)}
                className="w-full bg-slate-950 border border-slate-800 rounded-lg py-2 px-3 text-sm text-white focus:outline-none focus:border-indigo-500"
              >
                <option value="">-- Choose a template to auto-populate fields --</option>
                {templates.map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.name} (₹{t.amount})
                  </option>
                ))}
              </select>
            </div>
          )}

          {/* Amount and Date Fields */}
          <div className="grid grid-cols-2 gap-4">
            <div className="flex flex-col gap-1">
              <label className="text-xs font-semibold text-slate-400 uppercase tracking-wider">
                Amount (₹)
              </label>
              <input
                type="number"
                min="0.01"
                step="0.01"
                required
                placeholder="0.00"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                className="w-full bg-slate-950 border border-slate-800 rounded-lg py-2 px-3 text-sm text-white focus:outline-none focus:border-indigo-500"
              />
            </div>
            <div className="flex flex-col gap-1">
              <label className="text-xs font-semibold text-slate-400 uppercase tracking-wider">
                Transaction Date
              </label>
              <div className="relative flex items-center">
                <input
                  type="date"
                  required
                  value={date}
                  onChange={(e) => setDate(e.target.value)}
                  onClick={(e) => { try { (e.currentTarget as HTMLInputElement).showPicker(); } catch {} }}
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg py-2 pl-3 pr-10 text-sm text-white focus:outline-none focus:border-indigo-500 cursor-pointer hide-native-datepicker"
                />
                <svg className="w-4 h-4 text-indigo-400 absolute right-3 pointer-events-none" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
                </svg>
              </div>
            </div>
          </div>

          {/* Voucher Number & Company — Voucher shown only for Expense */}
          <div className={`grid gap-4 ${txType === 'EXPENSE' ? 'grid-cols-2' : 'grid-cols-1'}`}>
            {txType === 'EXPENSE' && (
              <div className="flex flex-col gap-1">
              <label className="text-xs font-semibold text-slate-400 uppercase tracking-wider font-bold">
                  Voucher Number <span className="text-slate-600 normal-case font-normal">(Optional)</span>
                </label>
                <input
                  type="text"
                  placeholder="e.g. Voc-001 — can be added later"
                  value={voucherNumber}
                  onChange={(e) => setVoucherNumber(e.target.value)}
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg py-2 px-3 text-sm text-white focus:outline-none focus:border-indigo-500"
                />
              </div>
            )}
            <div className="flex flex-col gap-1">
              <label className="text-xs font-semibold text-slate-400 uppercase tracking-wider font-bold">
                Company
              </label>
              <select
                required
                value={company}
                onChange={(e) => setCompany(e.target.value)}
                className="w-full bg-slate-950 border border-slate-800 rounded-lg py-2 px-3 text-sm text-white focus:outline-none focus:border-indigo-500"
              >
                {companies.map((c) => (
                  <option key={c} value={c}>
                    {c}
                  </option>
                ))}
              </select>
            </div>
          </div>

          {/* Description */}
          <div className="flex flex-col gap-1">
            <label className="text-xs font-semibold text-slate-400 uppercase tracking-wider">
              {txType === 'EXPENSE' ? 'What was it for?' : 'Source / note'}
            </label>
            <input
              type="text"
              required
              placeholder={txType === 'EXPENSE' ? 'e.g. Courier charges, refreshments, office stationery' : 'e.g. Replenishment from Accounts'}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              className="w-full bg-slate-950 border border-slate-800 rounded-lg py-2 px-3 text-sm text-white focus:outline-none focus:border-indigo-500"
            />
          </div>

          {/* Category, Subcategory and Payee (Only for Expense) */}
          {txType === 'EXPENSE' && (
            <>
              <div className="grid grid-cols-2 gap-4">
                <div className="flex flex-col gap-1">
                  <label className="text-xs font-semibold text-slate-400 uppercase tracking-wider">
                    Category
                  </label>
                  <select
                    required
                    value={selectedCategoryId}
                    onChange={(e) => {
                      setSelectedCategoryId(e.target.value ? Number(e.target.value) : '');
                      setSelectedSubcategoryId(''); // reset subcategory on category change
                    }}
                    className="w-full bg-slate-950 border border-slate-800 rounded-lg py-2 px-3 text-sm text-white focus:outline-none focus:border-indigo-500"
                  >
                    <option value="">-- Choose Category --</option>
                    {categories.map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.name}
                      </option>
                    ))}
                  </select>
                </div>
                <div className="flex flex-col gap-1">
                  <label className="text-xs font-semibold text-slate-400 uppercase tracking-wider">
                    Subcategory (optional)
                  </label>
                  <select
                    value={selectedSubcategoryId}
                    onChange={(e) => setSelectedSubcategoryId(e.target.value ? Number(e.target.value) : '')}
                    disabled={!selectedCategoryId}
                    className="w-full bg-slate-950 border border-slate-800 rounded-lg py-2 px-3 text-sm text-white focus:outline-none focus:border-indigo-500 disabled:opacity-50 disabled:cursor-not-allowed"
                  >
                    <option value="">-- Choose Subcategory --</option>
                    {selectedCategory?.subcategories.map((s) => (
                      <option key={s.id} value={s.id}>
                        {s.name}
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              <div className="flex flex-col gap-1">
                <label className="text-xs font-semibold text-slate-400 uppercase tracking-wider">
                  Payee / Vendor / Recipient
                </label>
                <input
                  type="text"
                  placeholder="e.g. Blue Dart Courier, Local Vendor Office Store"
                  value={payee}
                  onChange={(e) => setPayee(e.target.value)}
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg py-2 px-3 text-sm text-white focus:outline-none focus:border-indigo-500"
                />
              </div>
            </>
          )}

          {/* HTML5 Drag & Drop File Upload Field */}
          <div className="flex flex-col gap-1">
            <label className="text-xs font-semibold text-slate-400 uppercase tracking-wider font-bold">
              Receipt / Invoice Attachment (optional)
            </label>
            <div
              onDragOver={handleDragOver}
              onDragLeave={handleDragLeave}
              onDrop={handleDrop}
              className={`border-2 border-dashed rounded-lg p-5 text-center flex flex-col items-center justify-center cursor-pointer transition ${
                dragOver
                  ? 'border-indigo-500 bg-indigo-950/20'
                  : file
                  ? 'border-emerald-500 bg-emerald-950/10'
                  : 'border-slate-800 hover:border-slate-700 bg-slate-950/50'
              }`}
              onClick={() => document.getElementById('tx-receipt-file')?.click()}
            >
              <input
                id="tx-receipt-file"
                type="file"
                className="hidden"
                accept="image/*,application/pdf"
                multiple
                onChange={handleFileChange}
              />
              {file.length > 0 ? (
                <div className="w-full flex flex-col items-center gap-2" onClick={(e) => e.stopPropagation()}>
                  {/* Thumbnail strip */}
                  <div className="w-full flex gap-2 overflow-x-auto pb-1 justify-center flex-wrap">
                    {file.map((f, idx) => {
                      const previewUrl = filePreviewUrls[idx];
                      return (
                        <div key={f.name + idx} className="relative group flex-shrink-0">
                          {f.type.startsWith('image/') && previewUrl ? (
                            <img
                              src={previewUrl}
                              alt={f.name}
                              className="h-20 w-20 object-cover rounded-lg border border-slate-700 bg-slate-950"
                            />
                          ) : f.type === 'application/pdf' ? (
                            <div className="h-20 w-20 flex flex-col items-center justify-center rounded-lg border border-slate-700 bg-slate-950">
                              <span className="text-red-400 font-black text-xs">PDF</span>
                              <span className="text-[9px] text-slate-500 text-center px-1 truncate w-full mt-0.5">{f.name}</span>
                            </div>
                          ) : (
                            <div className="h-20 w-20 flex flex-col items-center justify-center rounded-lg border border-slate-700 bg-slate-950">
                              <span className="text-2xl">📄</span>
                              <span className="text-[9px] text-slate-500 text-center px-1 truncate w-full mt-0.5">{f.name}</span>
                            </div>
                          )}
                          {/* Remove individual file button */}
                          <button
                            type="button"
                            onClick={() => setFile((prev) => prev.filter((_, i) => i !== idx))}
                            className="absolute -top-1 -right-1 w-4 h-4 rounded-full bg-red-600 text-white text-[9px] font-bold flex items-center justify-center opacity-0 group-hover:opacity-100 transition shadow"
                            title={`Remove ${f.name}`}
                          >
                            ✕
                          </button>
                        </div>
                      );
                    })}
                  </div>

                  <div className="text-[10px] text-emerald-400 font-semibold">
                    {file.length} file{file.length > 1 ? 's' : ''} selected
                    {' '}· {(file.reduce((s, f) => s + f.size, 0) / 1024 / 1024).toFixed(2)} MB total
                  </div>

                  <div className="flex gap-2 mt-1">
                    <button
                      type="button"
                      onClick={() => setFile([])}
                      className="text-[10px] text-red-400 hover:text-red-300 font-bold bg-red-950/40 border border-red-900/40 rounded-md px-2.5 py-1 transition flex items-center gap-1"
                    >
                      ✕ Remove All
                    </button>
                    <button
                      type="button"
                      onClick={() => document.getElementById('tx-receipt-file')?.click()}
                      className="text-[10px] text-slate-300 hover:text-white font-bold bg-slate-900 border border-slate-800 rounded-md px-2.5 py-1 transition"
                    >
                      ＋ Add More
                    </button>
                  </div>
                </div>
              ) : (
                <>
                  <div className="text-2xl mb-1 text-slate-500">📥</div>
                  <div className="text-xs text-slate-300 font-medium">
                    Drag and drop file here, or <span className="text-indigo-400 underline">browse</span>
                  </div>
                  <div className="text-[10px] text-slate-600 mt-1">
                    Accepts PDF or Images (Max 10MB)
                  </div>
                </>
              )}
            </div>
          </div>

          {/* Actions */}
          <div className="flex justify-end gap-3 mt-6 border-t border-slate-800/60 pt-4">
            <button
              type="button"
              onClick={onClose}
              disabled={loading}
              className="py-2 px-4 rounded-lg bg-slate-950 border border-slate-800 text-xs font-bold text-slate-300 hover:text-slate-100 hover:bg-slate-900 transition"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={loading}
              className={`py-2 px-4 rounded-lg text-xs font-bold text-white shadow-lg hover:shadow-indigo-500/20 transition flex items-center gap-2 ${
                txType === 'EXPENSE'
                  ? 'bg-indigo-600 hover:bg-indigo-500'
                  : 'bg-emerald-600 hover:bg-emerald-500'
              }`}
            >
              {loading ? (
                <>
                  <span className="w-3.5 h-3.5 border-2 border-white/35 border-t-white rounded-full animate-spin"></span>
                  Saving...
                </>
              ) : (
                'Save Transaction'
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
