import React, { useState, useEffect, useRef } from 'react';
import { todayISO } from '../utils';

export default function TransactionModal({ modal, state, onClose, onSave, onSetType, showToast }) {
  const { type, editId } = modal;
  const existing = editId ? state.transactions.find(t => t.id === editId) : null;

  const [amount, setAmount] = useState('');
  const [date, setDate] = useState(todayISO());
  const [desc, setDesc] = useState('');
  const [cat, setCat] = useState(state.categories[0] || '');
  const [mem, setMem] = useState(state.members[0] || '');
  const [receipt, setReceipt] = useState('received');
  const [receiptNo, setReceiptNo] = useState('');
  const amountRef = useRef();

  useEffect(() => {
    if (existing) {
      setAmount(existing.amount);
      setDate(existing.date);
      setDesc(existing.description);
      setCat(existing.category || state.categories[0] || '');
      setMem(existing.paidBy || state.members[0] || '');
      setReceipt(existing.receiptStatus || 'received');
      setReceiptNo(existing.receiptNo || '');
    } else {
      setAmount('');
      setDate(todayISO());
      setDesc('');
      setCat(state.categories[0] || '');
      setMem(state.members[0] || '');
      setReceipt('received');
      setReceiptNo('');
    }
    setTimeout(() => amountRef.current?.focus(), 50);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleSave = () => {
    const amt = parseFloat(amount);
    if (!amt || amt <= 0) { showToast('⚠️ Enter a valid amount'); return; }
    if (!date) { showToast('⚠️ Pick a date'); return; }
    if (!desc.trim()) { showToast('⚠️ Add a short description'); return; }
    onSave({
      type,
      amount: amt,
      date,
      description: desc.trim(),
      category: type === 'expense' ? cat : '',
      paidBy: type === 'expense' ? mem : '',
      receiptStatus: type === 'expense' ? receipt : 'na',
      receiptNo: type === 'expense' ? receiptNo.trim() : '',
    });
  };

  const isExp = type === 'expense';

  return (
    <div className="overlay" onClick={e => { if (e.target === e.currentTarget) onClose(); }}>
      <div className="modal">
        <h3>{editId ? 'Edit Transaction' : (isExp ? 'Record Expense' : 'Add Cash to Box')}</h3>

        {!editId && (
          <div className="seg">
            <button className={isExp ? 'on' : ''} onClick={() => onSetType('expense')}>📉 Expense</button>
            <button className={!isExp ? 'on' : ''} onClick={() => onSetType('topup')}>💵 Cash Top-up</button>
          </div>
        )}

        <div className="frow">
          <div className="field">
            <label>Amount (₹)</label>
            <input ref={amountRef} type="number" value={amount} onChange={e => setAmount(e.target.value)} min="0" step="0.01" placeholder="0.00" />
          </div>
          <div className="field">
            <label>Date</label>
            <input type="date" value={date} onChange={e => setDate(e.target.value)} />
          </div>
        </div>

        <div className="field">
          <label>{isExp ? 'What was it for?' : 'Source / note'}</label>
          <input
            value={desc}
            onChange={e => setDesc(e.target.value)}
            placeholder={isExp ? 'e.g. Courier charges, milk & tea supplies' : 'e.g. Monthly replenishment from Accounts'}
          />
        </div>

        {isExp && (
          <>
            <div className="frow">
              <div className="field">
                <label>Category</label>
                <select value={cat} onChange={e => setCat(e.target.value)}>
                  {state.categories.map(c => <option key={c}>{c}</option>)}
                </select>
              </div>
              <div className="field">
                <label>Paid by</label>
                <select value={mem} onChange={e => setMem(e.target.value)}>
                  {state.members.map(m => <option key={m}>{m}</option>)}
                </select>
              </div>
            </div>
            <div className="frow">
              <div className="field">
                <label>Receipt</label>
                <select value={receipt} onChange={e => setReceipt(e.target.value)}>
                  <option value="received">✅ Received</option>
                  <option value="pending">⏳ Pending</option>
                  <option value="na">— Not applicable</option>
                </select>
              </div>
              <div className="field">
                <label>Receipt no. (optional)</label>
                <input value={receiptNo} onChange={e => setReceiptNo(e.target.value)} placeholder="e.g. RCP-104" />
              </div>
            </div>
          </>
        )}

        <div className="modal-foot">
          <button className="btn btn-ghost" onClick={onClose}>Cancel</button>
          <button className="btn btn-primary" onClick={handleSave}>Save</button>
        </div>
      </div>
    </div>
  );
}
