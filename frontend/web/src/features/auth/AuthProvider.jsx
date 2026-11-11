import { useState, useEffect, useRef, useCallback } from 'react';
import { authRepo } from './repositories/auth.repo';
import { isRemoteAuth } from './config';
import { readSession, clearSession, SESSION_ENDED, SESSION_KEY_NAME } from './session';

import { AuthContext } from './AuthContext';

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [sessionError, setSessionError] = useState(null);
  const generation = useRef(0);

  const restore = useCallback(async () => {
    const current = ++generation.current;
    setLoading(true);
    setSessionError(null);
    try {
      const restored = await authRepo.getCurrentUser();
      if (current === generation.current) setUser(restored);
    } catch (error) {
      if (current === generation.current) {
        setUser(null);
        if (error.status !== 401) setSessionError(error);
      }
    } finally { if (current === generation.current) setLoading(false); }
  }, []);

  useEffect(() => {
    let active = true;
    const invalidate = () => { generation.current++; };
    const ended = () => { generation.current++; setUser(null); setSessionError(null); setLoading(false); };
    const storage = event => { if (event.key === SESSION_KEY_NAME || event.key === null) void restore(); };
    window.addEventListener(SESSION_ENDED, ended);
    window.addEventListener('storage', storage);
    queueMicrotask(() => { if (active) void restore(); });
    return () => {
      active = false;
      invalidate();
      window.removeEventListener(SESSION_ENDED, ended);
      window.removeEventListener('storage', storage);
    };
  }, [restore]);

  useEffect(() => {
    if (!isRemoteAuth || !user) return;
    const session = readSession();
    if (!session) return;
    const expire = () => {
      if (Date.now() >= session.expiresAt) clearSession(session.accessToken);
    };
    const timer = setTimeout(expire, Math.max(0, session.expiresAt - Date.now()));
    window.addEventListener('focus', expire);
    return () => { clearTimeout(timer); window.removeEventListener('focus', expire); };
  }, [user]);

  const login = async credentials => {
    const current = ++generation.current;
    const result = await authRepo.login(credentials);
    if (current === generation.current) { setUser(result.user); setSessionError(null); }
    return result.user;
  };
  const logout = async () => {
    generation.current++;
    try { await authRepo.logout(); return { revocationFailed: false }; }
    catch (error) { return { revocationFailed: error.status !== 401 }; }
    finally { setUser(null); setSessionError(null); }
  };
  const updateProfile = async updates => {
    const current = generation.current;
    const updated = await authRepo.updateProfile(updates);
    if (current === generation.current) setUser(updated);
    return updated;
  };
  const changePassword = async payload => {
    await authRepo.changePassword(payload);
    generation.current++;
    setUser(null);
  };

  return <AuthContext.Provider value={{ user, loading, sessionError, restore, login, logout, updateProfile, changePassword,
    register: payload => authRepo.register(payload), verifyEmail: payload => authRepo.verifyEmail(payload),
    resendOtp: email => authRepo.resendOtp(email) }}>
    {children}
  </AuthContext.Provider>;
}
