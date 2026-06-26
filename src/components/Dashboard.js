import React from 'react';
import { Bar, Doughnut } from 'react-chartjs-2';
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  BarElement,
  ArcElement,
  Title,
  Tooltip,
  Legend,
} from 'chart.js';
import { fmt, monthKey, localISO, sumMonth, pendingReceipts, balance, lastNMonths, PALETTE, fmtDate } from '../utils';

ChartJS.register(CategoryScale, LinearScale, BarElement, ArcElement, Title, Tooltip, Legend);

export default function Dashboard({ state, openTxModal }) {
  const now = new Date();
  const mk = localISO(now).slice(0, 7);
  const monthName = now.toLocaleDateString('en-IN', { month: 'long' });
  const todayStr = now.toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });

  const bal = balance(state.transactions);
  const spent = sumMonth(state.transactions, 'expense', mk);
  const added = sumMonth(state.transactions, 'topup', mk);
  const pending = pendingReceipts(state.transactions);
  const expCount = state.transactions.filter(t => t.type === 'expense' && monthKey(t.date) === mk).length;

  // Trend chart
  const months = lastNMonths(6);
  const trendData = {
    labels: months.map(m => new Date(m + '-01T00:00').toLocaleDateString('en-IN', { month: 'short' })),
    datasets: [
      { label: 'Spent', data: months.map(m => sumMonth(state.transactions, 'expense', m)), backgroundColor: 'rgba(244,63,94,.75)', borderRadius: 7, maxBarThickness: 30 },
      { label: 'Added', data: months.map(m => sumMonth(state.transactions, 'topup', m)), backgroundColor: 'rgba(16,185,129,.75)', borderRadius: 7, maxBarThickness: 30 },
    ],
  };
  const trendOptions = {
    responsive: true, maintainAspectRatio: false,
    plugins: {
      legend: { position: 'bottom', labels: { usePointStyle: true, boxWidth: 8, font: { family: 'Inter', weight: '600' } } },
      tooltip: { callbacks: { label: c => ` ${c.dataset.label}: ${fmt(c.raw)}` } },
    },
    scales: {
      y: { grid: { color: '#eef2f7' }, ticks: { callback: v => '₹' + (v >= 1000 ? (v / 1000) + 'k' : v) } },
      x: { grid: { display: false } },
    },
  };

  // Category chart
  const by = {};
  state.transactions.filter(t => t.type === 'expense').forEach(t => {
    by[t.category] = (by[t.category] || 0) + +t.amount;
  });
  const entries = Object.entries(by).sort((a, b) => b[1] - a[1]);
  const catData = {
    labels: entries.map(e => e[0]),
    datasets: [{ data: entries.map(e => e[1]), backgroundColor: PALETTE, borderWidth: 2, borderColor: '#fff' }],
  };
  const catOptions = {
    responsive: true, maintainAspectRatio: false, cutout: '62%',
    plugins: {
      legend: { position: 'bottom', labels: { usePointStyle: true, boxWidth: 8, font: { family: 'Inter', size: 11, weight: '600' } } },
      tooltip: { callbacks: { label: c => ` ${c.label}: ${fmt(c.raw)}` } },
    },
  };

  // Top categories
  const topCats = entries.slice(0, 5);
  const max = topCats.length ? topCats[0][1] : 1;

  // Recent transactions
  const recent = [...state.transactions]
    .sort((a, b) => b.date.localeCompare(a.date) || b.id.localeCompare(a.id))
    .slice(0, 6);

  return (
    <section>
      <div className="page-head">
        <div>
          <h2>Dashboard</h2>
          <p>{todayStr}</p>
        </div>
        <div className="head-actions">
          <button className="btn btn-green" onClick={() => openTxModal('topup')}>＋ Add Cash</button>
          <button className="btn btn-primary" onClick={() => openTxModal('expense')}>＋ Record Expense</button>
        </div>
      </div>

      <div className="stats">
        <div className="stat hero">
          <div className="label">Cash in Hand</div>
          <div className="value">{fmt(bal)}</div>
          <div className="sub">
            {bal < state.lowThreshold
              ? <span className="low-warning">⚠️ Below ₹{(+state.lowThreshold).toLocaleString('en-IN')} threshold — time to top up</span>
              : 'Available in the cash box'}
          </div>
        </div>
        <div className="stat">
          <div className="chip-ico" style={{ background: 'var(--red-bg)' }}>📉</div>
          <div className="label">Spent · {monthName}</div>
          <div className="value" style={{ color: 'var(--red)' }}>{fmt(spent)}</div>
          <div className="sub">{expCount} expense{expCount === 1 ? '' : 's'} this month</div>
        </div>
        <div className="stat">
          <div className="chip-ico" style={{ background: 'var(--green-bg)' }}>💵</div>
          <div className="label">Added · {monthName}</div>
          <div className="value" style={{ color: 'var(--green)' }}>{fmt(added)}</div>
          <div className="sub">Cash added this month</div>
        </div>
        <div className="stat">
          <div className="chip-ico" style={{ background: 'var(--amber-bg)' }}>🧾</div>
          <div className="label">Pending Receipts</div>
          <div className="value" style={{ color: 'var(--amber)' }}>{pending.length}</div>
          <div className="sub">
            {pending.length
              ? fmt(pending.reduce((s, t) => s + +t.amount, 0)) + ' awaiting receipts'
              : 'All receipts collected 🎉'}
          </div>
        </div>
      </div>

      <div className="grid-2">
        <div className="card">
          <h3>Monthly Cash Flow</h3>
          <div className="card-sub">Spending vs top-ups · last 6 months</div>
          <div className="chart-wrap"><Bar data={trendData} options={trendOptions} /></div>
        </div>
        <div className="card">
          <h3>Where the Money Goes</h3>
          <div className="card-sub">
            {entries.length ? 'Spend by category · all time' : 'No expenses yet — record one to see insights'}
          </div>
          <div className="chart-wrap">
            {entries.length ? <Doughnut data={catData} options={catOptions} /> : null}
          </div>
        </div>
      </div>

      <div className="grid-2">
        <div className="card">
          <h3>Recent Activity</h3>
          <div className="card-sub">Last 6 transactions</div>
          {recent.length ? recent.map(t => (
            <div key={t.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '10px 0', borderBottom: '1px solid #f1f5f9' }}>
              <div className="t-desc">
                <div className="d1">{t.description}</div>
                <div className="d2">{fmtDate(t.date)} · {t.type === 'topup' ? 'Top-up' : t.category}{t.paidBy ? ' · ' + t.paidBy : ''}</div>
              </div>
              <div className={`t-amount ${t.type === 'topup' ? 'top' : 'exp'}`}>
                {t.type === 'topup' ? '+' : '−'}{fmt(t.amount)}
              </div>
            </div>
          )) : (
            <div className="empty"><div className="big">✨</div>No activity yet. Record your first expense!</div>
          )}
        </div>
        <div className="card">
          <h3>Top Spend Areas</h3>
          <div className="card-sub">All-time by category</div>
          {topCats.length ? (
            <div className="topcat">
              {topCats.map(([cat, val]) => (
                <div key={cat} className="topcat-row">
                  <div className="nm" title={cat}>{cat}</div>
                  <div className="bar"><i style={{ width: `${Math.max(4, val / max * 100)}%` }}></i></div>
                  <div className="amt">{fmt(val)}</div>
                </div>
              ))}
            </div>
          ) : (
            <div className="empty"><div className="big">📊</div>Insights appear once you record expenses.</div>
          )}
        </div>
      </div>
    </section>
  );
}
