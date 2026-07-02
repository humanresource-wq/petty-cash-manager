import { useEffect, useState } from 'react';
import { api } from './api/client';
import type { AppConfig, UserResponse } from './types';
import { Login } from './components/Login';
import { Dashboard } from './components/Dashboard';

function App() {
  const [currentUser, setCurrentUser] = useState<UserResponse | null>(null);
  const [config, setConfig] = useState<AppConfig | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    bootstrapApp();
  }, []);

  const bootstrapApp = async () => {
    setLoading(true);
    setError(null);
    try {
      // 1. Fetch Google Client ID and Demo configurations
      const appConfig = await api.auth.getConfig();
      setConfig(appConfig);

      // 2. Validate current session if token is stored in sessionStorage
      const token = sessionStorage.getItem('token');
      if (token) {
        const user = await api.auth.getMe();
        setCurrentUser(user);
      }
    } catch (err) {
      console.error('App bootstrap error:', err);
      // Clean up token on auth failure
      sessionStorage.removeItem('token');
      setError('Session expired or backend service offline. Please try logging in again.');
    } finally {
      setLoading(false);
    }
  };

  const handleLoginSuccess = (user: UserResponse) => {
    setCurrentUser(user);
  };

  const handleLogout = () => {
    sessionStorage.removeItem('token');
    setCurrentUser(null);
  };

  if (loading) {
    return (
      <div className="min-h-screen w-full flex flex-col items-center justify-center bg-slate-950 text-slate-400 gap-3">
        <span className="w-8 h-8 border-4 border-slate-800 border-t-indigo-500 rounded-full animate-spin"></span>
        <span className="text-xs font-semibold">Configuring secure access gateway...</span>
      </div>
    );
  }

  // If there's an initialization error and no config could be fetched
  if (error && !config) {
    return (
      <div className="min-h-screen w-full flex items-center justify-center bg-slate-950 px-4">
        <div className="max-w-md bg-slate-900 border border-slate-800 rounded-xl p-6 shadow-2xl text-center flex flex-col items-center gap-4">
          <div className="w-12 h-12 bg-red-950/40 text-red-500 border border-red-900/60 rounded-full flex items-center justify-center text-lg">
            ⚠️
          </div>
          <h3 className="font-extrabold text-white text-base">Backend Connection Offline</h3>
          <p className="text-xs text-slate-400 leading-relaxed">
            The frontend is unable to establish a secure handshake with the Petty Cash backend. Make sure your Docker container services are active.
          </p>
          <button
            onClick={bootstrapApp}
            className="mt-2 py-2 px-4 bg-indigo-650 hover:bg-indigo-600 text-white font-bold text-xs rounded-lg transition"
          >
            Retry Connection
          </button>
        </div>
      </div>
    );
  }

  return (
    <>
      {currentUser && config ? (
        <Dashboard currentUser={currentUser} onLogout={handleLogout} />
      ) : config ? (
        <Login config={config} onLoginSuccess={handleLoginSuccess} />
      ) : null}
    </>
  );
}

export default App;
