import React, { useState, useRef } from 'react';
import { fmt, fmtDate, monthKey } from '../utils';

export default function Transactions({ state, openTxModal, delTx, toggleReceipt, onExportCSV, onImportCSV }) {
  const [search, setSearch] = useState('');
  const [fType, setFType] = useState('');
  const [fCat, setFCat] = useState('');
  const [fReceipt, setFReceipt] = useState('');
  const [fMonth, setFMonth] = useState('');
  const csvRef = useRef();

  const q = search.toLowerCase();
  const rows = [...state.transactions]
    .sort((a, b) => b.date.localeCompare(a.date) || b.id.localeCompare(a.id))
    .filter(t =>
      (!fType || t.type === fType) &&
      (!fCat || t.category === fCat) &&
      (!fMonth || monthKey(t.date) === fMonth) &&
      (!fReceipt || (t.type === 'expense' && t.receiptStatus === fReceipt)) &&
      (!q || [t.description, t.paidBy, t.receiptNo, t.category].join(' ').toLowerCase().includes(q))
    );

  const receiptBadge = t => {
    if (t.type === 'topup') return <span className="badge rec-na">—</span>;
    if (t.receiptStatus === 'received') return <span className="badge rec-ok">✅ {t.receiptNo || 'Received'}</span>;
    if (t.receiptStatus === 'pending') return <span className="badge rec-pend" title="Click to mark received" onClick={() => toggleReceipt(t.id)}>⏳ Pending</span>;
    return <span className="badge rec-na">N/A</span>;
  };

  const handleFileChange = e => {
    if (e.target.files[0]) {
      onImportCSV(e.target.files[0]);
      e.target.value = '';
    }
  };

  return (
    <section>
      <div className="page-head">
        <div>
          <h2>Transactions</h2>
          <p>Every rupee in and out — tap an amber receipt badge to mark it received.</p>
        </div>
        <div className="head-actions">
          <button className="btn btn-ghost" onClick={() => csvRef.current.click()}>⬆ Import CSV</button>
          <button className="btn btn-ghost" onClick={onExportCSV}>⬇ Export Excel/CSV</button>
          <button className="btn btn-green" onClick={() => openTxModal('topup')}>＋ Add Cash</button>
          <button className="btn btn-primary" onClick={() => openTxModal('expense')}>＋ Record Expense</button>
        </div>
      </div>
      <input type="file" ref={csvRef} accept=".csv" style={{ display: 'none' }} onChange={handleFileChange} />

      <div className="tbl-card">
        <div className="tbl-toolbar">
          <input
            type="search"
            placeholder="🔍  Search description, member, receipt no…"
            value={search}
            onChange={e => setSearch(e.target.value)}
          />
          <select className="flt" value={fType} onChange={e => setFType(e.target.value)}>
            <option value="">All types</option>
            <option value="expense">Expenses</option>
            <option value="topup">Top-ups</option>
          </select>
          <select className="flt" value={fCat} onChange={e => setFCat(e.target.value)}>
            <option value="">All categories</option>
            {state.categories.map(c => <option key={c}>{c}</option>)}
          </select>
          <select className="flt" value={fReceipt} onChange={e => setFReceipt(e.target.value)}>
            <option value="">All receipts</option>
            <option value="pending">Receipt pending</option>
            <option value="received">Receipt received</option>
          </select>
          <input type="month" className="flt" value={fMonth} onChange={e => setFMonth(e.target.value)} />
        </div>
        <div style={{ overflowX: 'auto' }}>
          <table>
            <thead>
              <tr>
                <th>Date</th>
                <th>Description</th>
                <th>Category</th>
                <th>Paid by</th>
                <th>Receipt</th>
                <th style={{ textAlign: 'right' }}>Amount</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {rows.map(t => (
                <tr key={t.id}>
                  <td style={{ whiteSpace: 'nowrap' }}>{fmtDate(t.date)}</td>
                  <td className="t-desc"><div className="d1">{t.description}</div></td>
                  <td>
                    {t.type === 'topup'
                      ? <span className="badge topup">💵 Top-up</span>
                      : <span className="badge cat">{t.category}</span>}
                  </td>
                  <td>{t.paidBy || '—'}</td>
                  <td>{receiptBadge(t)}</td>
                  <td className={`t-amount ${t.type === 'topup' ? 'top' : 'exp'}`} style={{ textAlign: 'right' }}>
                    {t.type === 'topup' ? '+' : '−'}{fmt(t.amount)}
                  </td>
                  <td style={{ whiteSpace: 'nowrap', textAlign: 'right' }}>
                    <button className="btn-danger-ghost" title="Edit" onClick={() => openTxModal(t.type, t.id)}>✏️</button>
                    <button className="btn-danger-ghost" title="Delete" onClick={() => delTx(t.id)}>🗑</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {rows.length === 0 && (
          <div className="empty">
            <div className="big">🗂️</div>
            No transactions match. Add your first entry with the buttons above.
          </div>
        )}
      </div>
    </section>
  );
}
