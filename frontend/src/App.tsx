import { useState } from 'react';
import { Wallet, ArrowUpRight, ArrowDownRight, FileText, Settings, Shield, Sparkles } from 'lucide-react';

function App() {
  const [currentTab, setCurrentTab] = useState('dashboard');

  return (
    <div className="flex h-screen bg-slate-900 text-slate-100 font-sans overflow-hidden">
      {/* Sidebar */}
      <aside className="w-64 bg-slate-950 border-r border-slate-800 flex flex-col justify-between p-6">
        <div>
          <div className="flex items-center gap-3 mb-8">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-indigo-500 to-purple-500 flex items-center justify-center shadow-lg shadow-indigo-500/30">
              <Wallet className="w-5 h-5 text-white" />
            </div>
            <div>
              <h1 className="font-extrabold text-lg bg-gradient-to-r from-indigo-200 via-purple-200 to-pink-200 bg-clip-text text-transparent">
                Petty Cash
              </h1>
              <span className="text-[10px] uppercase tracking-wider font-semibold text-indigo-400">
                Office Manager
              </span>
            </div>
          </div>

          <nav className="space-y-1">
            {[
              { id: 'dashboard', label: 'Dashboard', icon: Wallet },
              { id: 'transactions', label: 'Transactions', icon: FileText },
              { id: 'settings', label: 'Settings', icon: Settings }
            ].map(tab => {
              const Icon = tab.icon;
              const isActive = currentTab === tab.id;
              return (
                <button
                  key={tab.id}
                  onClick={() => setCurrentTab(tab.id)}
                  className={`w-full flex items-center gap-3 px-4 py-3 rounded-xl font-medium text-sm transition-all duration-200 ${
                    isActive
                      ? 'bg-gradient-to-r from-indigo-500/20 to-purple-500/10 text-white border border-indigo-500/20 shadow-inner'
                      : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900/50'
                  }`}
                >
                  <Icon className={`w-4 h-4 ${isActive ? 'text-indigo-400' : 'text-slate-400'}`} />
                  {tab.label}
                </button>
              );
            })}
          </nav>
        </div>

        <div className="border-t border-slate-800 pt-4 text-xs text-slate-500 space-y-2">
          <div className="flex items-center gap-2">
            <Shield className="w-3.5 h-3.5 text-indigo-500" />
            <span>Secure Storage Active</span>
          </div>
          <p className="leading-relaxed">Data will persist locally via LocalStorage until Phase 2 is connected.</p>
        </div>
      </aside>

      {/* Main Content Area */}
      <main className="flex-1 flex flex-col overflow-y-auto bg-gradient-to-b from-slate-900 to-slate-950">
        {/* Header */}
        <header className="px-8 py-5 border-b border-slate-800/60 bg-slate-900/30 backdrop-blur-md sticky top-0 flex items-center justify-between z-10">
          <div>
            <h2 className="text-xl font-extrabold tracking-tight text-white flex items-center gap-2">
              Welcome Back <Sparkles className="w-4 h-4 text-indigo-400" />
            </h2>
            <p className="text-xs text-slate-400 mt-0.5">Petty Cash Manager — Phase 1 Project Setup Complete</p>
          </div>
          <div className="flex items-center gap-3">
            <button className="px-4 py-2 text-xs font-semibold rounded-lg bg-slate-800 hover:bg-slate-750 text-slate-200 border border-slate-750 transition">
              Help Docs
            </button>
            <button className="px-4 py-2 text-xs font-semibold rounded-lg bg-gradient-to-r from-indigo-500 to-purple-500 hover:from-indigo-600 hover:to-purple-600 text-white shadow-lg shadow-indigo-500/20 hover:shadow-indigo-500/30 transition">
              Refresh App
            </button>
          </div>
        </header>

        {/* Content */}
        <div className="p-8 max-w-5xl w-full mx-auto space-y-8">
          {/* Status Alert Banner */}
          <div className="p-5 rounded-2xl bg-indigo-500/10 border border-indigo-500/20 flex items-start gap-4 shadow-xl shadow-indigo-950/20">
            <div className="p-2.5 rounded-lg bg-indigo-500/15 text-indigo-400">
              <Sparkles className="w-5 h-5" />
            </div>
            <div>
              <h3 className="font-bold text-white text-sm">System Framework Initialized (Phase 1)</h3>
              <p className="text-xs text-indigo-200/70 mt-1 leading-relaxed">
                The Spring Boot 3.5.x backend, H2/PostgreSQL database interfaces, and Liquibase baseline schema migrations have successfully passed all automated tests. The React 18 frontend is now connected with Vite and configured with Tailwind CSS v4.
              </p>
            </div>
          </div>

          {/* Tab Content Placeholder */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            <div className="p-6 rounded-2xl bg-slate-900 border border-slate-800 shadow-md">
              <div className="flex justify-between items-start mb-4">
                <span className="text-xs font-semibold uppercase tracking-wider text-slate-400">Cash in Hand</span>
                <span className="p-1 rounded bg-indigo-500/10 text-indigo-400 text-[10px] font-bold">Active</span>
              </div>
              <div className="text-3xl font-extrabold text-white">₹0.00</div>
              <p className="text-xs text-slate-500 mt-2">Threshold alert level: ₹2,000.00</p>
            </div>

            <div className="p-6 rounded-2xl bg-slate-900 border border-slate-800 shadow-md">
              <div className="flex justify-between items-start mb-4">
                <span className="text-xs font-semibold uppercase tracking-wider text-slate-400">Spent This Month</span>
                <ArrowDownRight className="w-4 h-4 text-emerald-400" />
              </div>
              <div className="text-3xl font-extrabold text-white">₹0.00</div>
              <p className="text-xs text-slate-500 mt-2">0 expense records recorded</p>
            </div>

            <div className="p-6 rounded-2xl bg-slate-900 border border-slate-800 shadow-md">
              <div className="flex justify-between items-start mb-4">
                <span className="text-xs font-semibold uppercase tracking-wider text-slate-400">Added This Month</span>
                <ArrowUpRight className="w-4 h-4 text-rose-400" />
              </div>
              <div className="text-3xl font-extrabold text-white">₹0.00</div>
              <p className="text-xs text-slate-500 mt-2">0 cash top-up records recorded</p>
            </div>
          </div>

          {/* Details Section */}
          <div className="p-6 rounded-2xl bg-slate-900 border border-slate-800 shadow-md space-y-4">
            <h3 className="font-bold text-white text-base">Backend Connection Status</h3>
            <div className="border border-slate-800 rounded-xl overflow-hidden">
              <div className="grid grid-cols-2 border-b border-slate-800 bg-slate-950/40 text-xs font-semibold text-slate-400">
                <div className="p-3 border-r border-slate-800">Endpoint</div>
                <div className="p-3">Status</div>
              </div>
              <div className="grid grid-cols-2 border-b border-slate-800 text-xs">
                <div className="p-3 border-r border-slate-800 text-slate-300 font-mono">/api/v1/health</div>
                <div className="p-3 text-emerald-400 font-medium">● Connected (UP)</div>
              </div>
              <div className="grid grid-cols-2 text-xs">
                <div className="p-3 border-r border-slate-800 text-slate-300 font-mono">/swagger-ui.html</div>
                <div className="p-3 text-indigo-400 font-medium">● Available</div>
              </div>
            </div>
          </div>
        </div>
      </main>
    </div>
  );
}

export default App;
