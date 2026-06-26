import React, { useState, useEffect, useRef, useCallback } from 'react';
import Sidebar from './components/Sidebar';
import Dashboard from './components/Dashboard';
import Transactions from './components/Transactions';
import Settings from './components/Settings';
import TransactionModal from './components/TransactionModal';
import Toast from './components/Toast';
import {
  loadState, saveState, uid, DEFAULTS,
  exportCSV, exportJSON,
  parseCsvImport, parseJsonBackup, readFileAsText,
} from './utils';

function App() {
  const [state, setState] = useState(loadState);
  const [currentView, setCurrentView] = useState('dashboard');
  const [modal, setModal] = useState({ open: false, type: 'expense', editId: null });
  const [modalKey, setModalKey] = useState(0);
  const [toastState, setToastState] = useState({ show: false, msg: '' });
  const toastTimer = useRef(null);

  useEffect(() => { saveState(state); }, [state]);

  const showToast = useCallback((msg) => {
    setToastState({ show: true, msg });
    clearTimeout(toastTimer.current);
    toastTimer.current = setTimeout(() => setToastState(t => ({ ...t, show: false })), 2400);
  }, []);

  const openTxModal = useCallback((type, id = null) => {
    setState(s => {
      const resolvedType = id ? (s.transactions.find(t => t.id === id)?.type || type) : type;
      setModal({ open: true, type: resolvedType, editId: id });
      return s;
    });
    setModalKey(k => k + 1);
  }, []);

  const closeTxModal = useCallback(() => {
    setModal(m => ({ ...m, open: false }));
  }, []);

  const saveTx = useCallback((txData) => {
    setModal(currentModal => {
      setState(s => {
        if (currentModal.editId) {
          return { ...s, transactions: s.transactions.map(t => t.id === currentModal.editId ? { ...txData, id: currentModal.editId } : t) };
        }
        return { ...s, transactions: [...s.transactions, { ...txData, id: uid() }] };
      });
      showToast(currentModal.editId ? '✏️ Transaction updated' : (txData.type === 'expense' ? '📉 Expense recorded' : '💵 Cash added'));
      return { ...currentModal, open: false };
    });
  }, [showToast]);

  const delTx = useCallback((id) => {
    if (!window.confirm('Delete this transaction?')) return;
    setState(s => ({ ...s, transactions: s.transactions.filter(t => t.id !== id) }));
    showToast('🗑 Deleted');
  }, [showToast]);

  const toggleReceipt = useCallback((id) => {
    setState(s => ({ ...s, transactions: s.transactions.map(t => t.id === id ? { ...t, receiptStatus: 'received' } : t) }));
    showToast('🧾 Receipt marked received');
  }, [showToast]);

  const addCat = useCallback((name) => {
    if (!name.trim()) return;
    setState(s => {
      if (s.categories.includes(name)) { showToast('Category already exists'); return s; }
      return { ...s, categories: [...s.categories, name] };
    });
  }, [showToast]);

  const rmCat = useCallback((index) => {
    setState(s => {
      const cat = s.categories[index];
      if (s.transactions.some(t => t.category === cat)) { showToast('⚠️ In use by transactions — cannot remove'); return s; }
      return { ...s, categories: s.categories.filter((_, i) => i !== index) };
    });
  }, [showToast]);

  const addMem = useCallback((name) => {
    if (!name.trim()) return;
    setState(s => {
      if (s.members.includes(name)) { showToast('Member already exists'); return s; }
      return { ...s, members: [...s.members, name] };
    });
  }, [showToast]);

  const rmMem = useCallback((index) => {
    setState(s => {
      const mem = s.members[index];
      if (s.transactions.some(t => t.paidBy === mem)) { showToast('⚠️ In use by transactions — cannot remove'); return s; }
      return { ...s, members: s.members.filter((_, i) => i !== index) };
    });
  }, [showToast]);

  const saveThresh = useCallback((value) => {
    if (isNaN(value) || value < 0) { showToast('Enter a valid amount'); return; }
    setState(s => ({ ...s, lowThreshold: value }));
    showToast('✅ Threshold saved');
  }, [showToast]);

  const wipeAll = useCallback(() => {
    if (!window.confirm('Erase ALL data? This cannot be undone. Export a backup first!')) return;
    if (!window.confirm('Really sure? Everything will be deleted.')) return;
    localStorage.removeItem('pettycash_v1');
    setState(JSON.parse(JSON.stringify(DEFAULTS)));
    showToast('All data erased');
  }, [showToast]);

  const handleExportCSV = useCallback(() => {
    setState(s => { exportCSV(s); return s; });
    showToast('⬇ CSV exported — opens directly in Excel');
  }, [showToast]);

  const handleExportJSON = useCallback(() => {
    setState(s => { exportJSON(s); return s; });
    showToast('⬇ Backup downloaded');
  }, [showToast]);

  const handleImportCSV = useCallback(async (file) => {
    try {
      const text = await readFileAsText(file);
      setState(s => {
        const result = parseCsvImport(text, s.categories, s.members);
        showToast(`⬆ Imported ${result.count} row${result.count === 1 ? '' : 's'}${result.skipped ? ` · ${result.skipped} skipped` : ''}`);
        return {
          ...s,
          transactions: [...s.transactions, ...result.transactions],
          categories: result.categories,
          members: result.members,
        };
      });
    } catch (err) {
      showToast('⚠️ ' + (err.message || 'Could not read that CSV'));
    }
  }, [showToast]);

  const handleImportJSON = useCallback(async (file) => {
    try {
      const text = await readFileAsText(file);
      const newState = parseJsonBackup(text);
      if (!window.confirm(`Restore backup with ${newState.transactions.length} transactions? Current data will be replaced.`)) return;
      setState(newState);
      showToast('✅ Backup restored');
    } catch (err) {
      showToast('⚠️ Not a valid backup file');
    }
  }, [showToast]);

  return (
    <div className="app">
      <Sidebar currentView={currentView} setCurrentView={setCurrentView} />
      <main className="main">
        {currentView === 'dashboard' && (
          <Dashboard state={state} openTxModal={openTxModal} />
        )}
        {currentView === 'transactions' && (
          <Transactions
            state={state}
            openTxModal={openTxModal}
            delTx={delTx}
            toggleReceipt={toggleReceipt}
            onExportCSV={handleExportCSV}
            onImportCSV={handleImportCSV}
          />
        )}
        {currentView === 'settings' && (
          <Settings
            state={state}
            addCat={addCat}
            rmCat={rmCat}
            addMem={addMem}
            rmMem={rmMem}
            saveThresh={saveThresh}
            wipeAll={wipeAll}
            onExportJSON={handleExportJSON}
            onImportJSON={handleImportJSON}
          />
        )}
      </main>

      {modal.open && (
        <TransactionModal
          key={modalKey}
          modal={modal}
          state={state}
          onClose={closeTxModal}
          onSave={saveTx}
          onSetType={type => setModal(m => ({ ...m, type }))}
          showToast={showToast}
        />
      )}

      <Toast toastState={toastState} />
    </div>
  );
}

export default App;
