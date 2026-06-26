import React from 'react';

const NAV = [
  { id: 'dashboard', icon: '📊', label: 'Dashboard' },
  { id: 'transactions', icon: '🧾', label: 'Transactions' },
  { id: 'settings', icon: '⚙️', label: 'Settings' },
];

export default function Sidebar({ currentView, setCurrentView }) {
  return (
    <aside className="sidebar">
      <div className="logo">
        <div className="logo-badge">💸</div>
        <div>
          <h1>Expense Management</h1>
          <span>Office Manager</span>
        </div>
      </div>
      {NAV.map(({ id, icon, label }) => (
        <button
          key={id}
          className={`nav-btn${currentView === id ? ' active' : ''}`}
          onClick={() => setCurrentView(id)}
        >
          <span className="ico">{icon}</span>{label}
        </button>
      ))}
      <div className="sidebar-foot">
        Data is stored locally in this browser.<br />Export regularly as backup.
      </div>
    </aside>
  );
}
