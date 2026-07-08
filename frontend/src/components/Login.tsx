import React, { useEffect, useState } from 'react';
import { api } from '../api/client';
import type { AppConfig, UserResponse } from '../types';

interface LoginProps {
  onLoginSuccess: (user: UserResponse) => void;
  config: AppConfig;
}

export const Login: React.FC<LoginProps> = ({ onLoginSuccess, config }) => {
  const [selectedDemoUser, setSelectedDemoUser] = useState<string>('');
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    // Dynamic loading of Google Identity Services SDK
    if (config.googleClientId) {
      const script = document.createElement('script');
      script.src = 'https://accounts.google.com/gsi/client';
      script.async = true;
      script.defer = true;
      script.onload = () => {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        const g = (window as any).google;
        if (g?.accounts?.id) {
          g.accounts.id.initialize({
            client_id: config.googleClientId,
            callback: async (response: { credential?: string }) => {
              if (!response?.credential) {
                setError('Google sign-in was cancelled or failed.');
                return;
              }
              setLoading(true);
              setError(null);
              try {
                const res = await api.auth.loginGoogle(response.credential);
                sessionStorage.setItem('token', res.token);
                onLoginSuccess(res.user);
              } catch (err) {
                setError(err instanceof Error ? err.message : 'Google login failed');
              } finally {
                setLoading(false);
              }
            },
            auto_select: false,
            cancel_on_tap_outside: true,
          });

          const btnContainer = document.getElementById('google-btn-container');
          if (btnContainer) {
            g.accounts.id.renderButton(btnContainer, {
              theme: 'outline',
              size: 'large',
              width: 320,
              text: 'signin_with',
              shape: 'rectangular',
            });
          }
        }
      };
      document.head.appendChild(script);
    }
  }, [config.googleClientId, onLoginSuccess]);

  const handleDemoLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedDemoUser) return;
    setLoading(true);
    setError(null);
    try {
      const res = await api.auth.loginDemo(selectedDemoUser);
      sessionStorage.setItem('token', res.token);
      onLoginSuccess(res.user);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Demo login failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen w-full flex items-center justify-center bg-radial from-[#1e1b4b] via-[#0f172a] to-[#020617] px-4">
      <div className="relative w-full max-w-md">
        {/* Glow Effects */}
        <div className="absolute -inset-1 rounded-3xl bg-gradient-to-r from-indigo-500 via-purple-600 to-pink-500 opacity-30 blur-2xl transition duration-1000"></div>

        {/* Login Card */}
        <div className="relative w-full bg-slate-900/80 border border-slate-800 rounded-2xl p-8 shadow-2xl backdrop-blur-xl flex flex-col items-center">
          <div className="w-16 h-16 rounded-2xl bg-gradient-to-tr from-indigo-500 to-purple-600 flex items-center justify-center text-3xl shadow-lg shadow-indigo-500/20 mb-4 animate-bounce">
            💸
          </div>
          <h1 className="text-2xl font-extrabold text-white tracking-tight text-center mb-1">
            Petty Cash Manager
          </h1>
          <p className="text-sm text-slate-400 text-center mb-8">
            Manage and audit office expenses seamlessly.
          </p>

          {error && (
            <div className="w-full bg-red-950/50 border border-red-800/80 rounded-lg p-3 text-xs text-red-400 font-medium mb-6">
              ⚠️ {error}
            </div>
          )}

          {/* Google SSO Button Container */}
          {config.googleClientId ? (
            <div className="w-full flex flex-col items-center mb-6">
              <div id="google-btn-container" className="min-h-[44px] flex items-center justify-center"></div>
              {config.demoLoginEnabled && (
                <div className="relative w-full flex items-center my-6">
                  <div className="flex-grow border-t border-slate-800"></div>
                  <span className="flex-shrink mx-4 text-xs font-semibold text-slate-500 uppercase tracking-widest">
                    Or
                  </span>
                  <div className="flex-grow border-t border-slate-800"></div>
                </div>
              )}
            </div>
          ) : (
            !config.demoLoginEnabled && (
              <div className="w-full bg-amber-950/40 border border-amber-900/60 rounded-lg p-4 text-xs text-amber-400 text-center mb-6">
                Google OAuth is not configured and demo login is disabled. Please contact your system administrator.
              </div>
            )
          )}

          {/* Demo User Selection Dropdown */}
          {config.demoLoginEnabled && (
            <form onSubmit={handleDemoLogin} className="w-full flex flex-col gap-4">
              <div className="flex flex-col gap-1.5">
                <label className="text-xs font-semibold text-slate-400 uppercase tracking-wider">
                  Select Demo User Profile
                </label>
                <select
                  value={selectedDemoUser}
                  onChange={(e) => setSelectedDemoUser(e.target.value)}
                  disabled={loading}
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg py-2.5 px-3 text-sm text-white focus:outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-900/50 transition"
                >
                  <option value="">Choose a demo user account...</option>
                  {config.demoUsers.map((u) => (
                    <option key={u.id} value={u.id}>
                      {u.name} ({u.email})
                    </option>
                  ))}
                </select>
              </div>

              <button
                type="submit"
                disabled={!selectedDemoUser || loading}
                className="w-full py-2.5 px-4 rounded-lg bg-gradient-to-r from-indigo-600 to-purple-600 hover:from-indigo-500 hover:to-purple-500 text-sm font-bold text-white shadow-lg shadow-indigo-600/30 hover:shadow-indigo-500/40 disabled:opacity-50 disabled:cursor-not-allowed hover:-translate-y-[1px] active:translate-y-0 transition duration-150 flex items-center justify-center gap-2"
              >
                {loading ? (
                  <span className="w-4 h-4 border-2 border-white/35 border-t-white rounded-full animate-spin"></span>
                ) : (
                  'Sign In to Dashboard'
                )}
              </button>
            </form>
          )}

          <div className="mt-8 text-center text-[10px] text-slate-600 max-w-[280px]">
            Access restricted to authorized personnel in the Petty Cash Directory.
          </div>
        </div>
      </div>
    </div>
  );
};
