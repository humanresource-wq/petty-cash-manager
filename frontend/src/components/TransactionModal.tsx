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
}) => {
  const [txType, setTxType] = useState<'EXPENSE' | 'TOPUP'>('EXPENSE');
  const [amount, setAmount] = useState<string>('');
  const [date, setDate] = useState<string>('');
  const [description, setDescription] = useState<string>('');
  const [payee, setPayee] = useState<string>('');
  const [selectedCategoryId, setSelectedCategoryId] = useState<number | ''>('');
  const [selectedSubcategoryId, setSelectedSubcategoryId] = useState<number | ''>('');
  const [selectedTemplateId, setSelectedTemplateId] = useState<number | ''>('');
  const [file, setFile] = useState<File | null>(null);
  const [loading, setLoading] = useState<boolean>(false);
  const [dragOver, setDragOver] = useState<boolean>(false);

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
      setFile(null);

      // Force to EXPENSE if user is standard USER, otherwise defaultType
      if (currentUser.role === 'USER') {
        setTxType('EXPENSE');
      } else {
        setTxType(defaultType || 'EXPENSE');
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
    if (e.target.files && e.target.files[0]) {
      setFile(e.target.files[0]);
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
    if (e.dataTransfer.files && e.dataTransfer.files[0]) {
      setFile(e.dataTransfer.files[0]);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const numAmount = parseFloat(amount);
    if (isNaN(numAmount) || numAmount <= 0) {
      return toast('⚠️ Enter a valid positive amount.');
    }
    if (!date) return toast('⚠️ Select a valid transaction date.');
    if (!description.trim()) return toast('⚠️ Description must not be blank.');

    if (txType === 'EXPENSE') {
      if (!selectedCategoryId) {
        return toast('⚠️ Select an expense Category.');
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
      };

      formData.append(
        'request',
        new Blob([JSON.stringify(requestPayload)], { type: 'application/json' })
      );

      if (file) {
        formData.append('file', file);
      }

      await api.transactions.record(formData);
      toast(txType === 'EXPENSE' ? '📉 Expense recorded successfully!' : '💵 Cash added to box!');
      onSuccess();
      onClose();
    } catch (err) {
      toast('❌ Error: ' + (err instanceof Error ? err.message : String(err)));
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
              <input
                type="date"
                required
                value={date}
                onChange={(e) => setDate(e.target.value)}
                className="w-full bg-slate-950 border border-slate-800 rounded-lg py-2 px-3 text-sm text-white focus:outline-none focus:border-indigo-500"
              />
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
                onChange={handleFileChange}
              />
              {file ? (
                <>
                  <div className="text-2xl mb-1">📄</div>
                  <div className="text-xs font-bold text-emerald-400 max-w-[240px] truncate">
                    {file.name}
                  </div>
                  <div className="text-[10px] text-slate-500">
                    {(file.size / 1024 / 1024).toFixed(2)} MB · Tap to replace
                  </div>
                </>
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
