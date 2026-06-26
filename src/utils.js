export const LS_KEY = 'pettycash_v1';

export const DEFAULTS = {
  transactions: [],
  categories: ['Stationery', 'Pantry & Refreshments', 'Courier & Postage', 'Travel & Conveyance', 'Repairs & Maintenance', 'Cleaning Supplies', 'Utilities', 'Miscellaneous'],
  members: ['Tony'],
  lowThreshold: 2000,
};

export const PALETTE = ['#6366f1', '#a855f7', '#ec4899', '#f59e0b', '#10b981', '#06b6d4', '#f43f5e', '#84cc16', '#8b5cf6', '#14b8a6'];

export const uid = () => Date.now().toString(36) + Math.random().toString(36).slice(2, 7);
export const fmt = n => '₹' + (+n).toLocaleString('en-IN', { maximumFractionDigits: 2 });
export const monthKey = d => d.slice(0, 7);
export const localISO = d => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
export const todayISO = () => localISO(new Date());
export const fmtDate = d => new Date(d + 'T00:00').toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });

export function loadState() {
  try {
    const s = JSON.parse(localStorage.getItem(LS_KEY));
    if (s && Array.isArray(s.transactions)) return { ...DEFAULTS, ...s };
  } catch (e) {}
  return JSON.parse(JSON.stringify(DEFAULTS));
}

export function saveState(state) {
  localStorage.setItem(LS_KEY, JSON.stringify(state));
}

export const balance = transactions =>
  transactions.reduce((s, t) => s + (t.type === 'topup' ? +t.amount : -t.amount), 0);

export const sumMonth = (transactions, type, mk) =>
  transactions.filter(t => t.type === type && monthKey(t.date) === mk).reduce((s, t) => s + +t.amount, 0);

export const pendingReceipts = transactions =>
  transactions.filter(t => t.type === 'expense' && t.receiptStatus === 'pending');

export function lastNMonths(n) {
  const out = [];
  const d = new Date();
  d.setDate(1);
  for (let i = n - 1; i >= 0; i--) {
    const x = new Date(d.getFullYear(), d.getMonth() - i, 1);
    out.push(localISO(x).slice(0, 7));
  }
  return out;
}

// ==================== CSV / JSON Export ====================
function csvCell(v) {
  v = String(v ?? '');
  return /[",\n]/.test(v) ? '"' + v.replace(/"/g, '""') + '"' : v;
}

export function exportCSV(state) {
  const head = ['Date', 'Type', 'Amount', 'Category', 'Description', 'Paid By', 'Receipt Status', 'Receipt No'];
  const sorted = [...state.transactions].sort((a, b) => a.date.localeCompare(b.date));
  let run = 0;
  const rows = sorted.map(t => {
    run += t.type === 'topup' ? +t.amount : -t.amount;
    return [...[t.date, t.type, t.amount, t.category, t.description, t.paidBy, t.receiptStatus, t.receiptNo].map(csvCell), run].join(',');
  });
  const csv = '﻿' + head.join(',') + ',Running Balance\n' + rows.join('\n');
  download(new Blob([csv], { type: 'text/csv;charset=utf-8' }), 'expense-' + todayISO() + '.csv');
}

export function exportJSON(state) {
  download(new Blob([JSON.stringify(state, null, 2)], { type: 'application/json' }), 'expense-backup-' + todayISO() + '.json');
}

export function download(blob, name) {
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = name;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(a.href), 2000);
}

export function readFileAsText(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = e => resolve(e.target.result);
    reader.onerror = reject;
    reader.readAsText(file);
  });
}

// ==================== CSV Import ====================
function parseCSV(text) {
  const rows = [];
  let row = [], cell = '', inQ = false;
  text = text.replace(/^﻿/, '');
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (inQ) {
      if (c === '"') { if (text[i + 1] === '"') { cell += '"'; i++; } else inQ = false; }
      else cell += c;
    } else if (c === '"') inQ = true;
    else if (c === ',') { row.push(cell); cell = ''; }
    else if (c === '\n' || c === '\r') {
      if (c === '\r' && text[i + 1] === '\n') i++;
      row.push(cell); cell = '';
      if (row.some(x => x !== '')) rows.push(row);
      row = [];
    } else cell += c;
  }
  if (cell !== '' || row.length) { row.push(cell); if (row.some(x => x !== '')) rows.push(row); }
  return rows;
}

function normDate(s) {
  s = s.trim();
  if (/^\d{4}-\d{2}-\d{2}/.test(s)) return s.slice(0, 10);
  const m = s.match(/^(\d{1,2})[/-](\d{1,2})[/-](\d{2,4})$/);
  if (m) {
    let [, d, mo, y] = m;
    if (y.length === 2) y = '20' + y;
    return `${y}-${mo.padStart(2, '0')}-${d.padStart(2, '0')}`;
  }
  const dt = new Date(s);
  return isNaN(dt) ? null : localISO(dt);
}

export function parseCsvImport(text, existingCategories, existingMembers) {
  const rows = parseCSV(text);
  if (rows.length < 2) throw new Error('No data rows found');

  const head = rows[0].map(h => h.toLowerCase().trim());
  const col = names => head.findIndex(h => names.some(n => h.includes(n)));
  const iDate = col(['date']), iAmt = col(['amount', 'amt']), iType = col(['type']);
  const iCat = col(['category', 'head']), iDesc = col(['desc', 'particular', 'detail', 'narration']);
  const iPaid = col(['paid', 'member', 'by']), iRec = col(['receipt status', 'status']), iRecNo = col(['receipt no', 'voucher', 'ref']);

  if (iDate < 0 || iAmt < 0) throw new Error('CSV needs at least Date and Amount columns');

  const transactions = [];
  const categories = [...existingCategories];
  const members = [...existingMembers];
  let count = 0, skipped = 0;

  rows.slice(1).forEach(r => {
    const date = normDate(r[iDate] || '');
    const amount = parseFloat(String(r[iAmt]).replace(/[₹,\s]/g, ''));
    if (!date || !amount || amount <= 0) { skipped++; return; }

    const typeRaw = (iType >= 0 ? r[iType] : '').toLowerCase();
    const type = /top|credit|in|add|deposit/.test(typeRaw) ? 'topup' : 'expense';
    let category = iCat >= 0 ? (r[iCat] || '').trim() : '';
    if (type === 'expense') {
      if (!category) category = 'Miscellaneous';
      if (!categories.includes(category)) categories.push(category);
    }
    let paidBy = iPaid >= 0 ? (r[iPaid] || '').trim() : '';
    if (paidBy && !members.includes(paidBy)) members.push(paidBy);
    const recRaw = (iRec >= 0 ? r[iRec] : '').toLowerCase();
    const receiptStatus = type === 'topup' ? 'na' : (/pend|await|no/.test(recRaw) ? 'pending' : 'received');

    transactions.push({
      id: uid(), type, amount, date,
      description: iDesc >= 0 ? (r[iDesc] || '').trim() || '(imported)' : '(imported)',
      category: type === 'expense' ? category : '',
      paidBy: type === 'expense' ? paidBy : '',
      receiptStatus,
      receiptNo: iRecNo >= 0 ? (r[iRecNo] || '').trim() : '',
    });
    count++;
  });

  return { transactions, categories, members, count, skipped };
}

// ==================== JSON Import ====================
export function parseJsonBackup(text) {
  const s = JSON.parse(text);
  if (!s || !Array.isArray(s.transactions)) throw new Error('Invalid backup file');
  return { ...DEFAULTS, ...s };
}
