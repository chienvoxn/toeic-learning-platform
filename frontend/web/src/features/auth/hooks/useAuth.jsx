import React, { createContext, useContext, useState, useEffect } from 'react';
import { authRepo } from '../repositories/auth.repo';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    authRepo.getCurrentUser()
      .then(u => setUser(u))
      .catch(() => setUser(null))
      .finally(() => setLoading(false));
  }, []);

  const login = async (credentials) => {
    const { user: loggedInUser } = await authRepo.login(credentials);
    setUser(loggedInUser);
    return loggedInUser;
  };

  const loginWithGoogle = async (idToken) => {
    const { user: loggedInUser } = await authRepo.loginWithGoogle(idToken);
    setUser(loggedInUser);
    return loggedInUser;
  };

  const register = async (payload) => {
    const { user: registeredUser } = await authRepo.register(payload);
    setUser(registeredUser);
    return registeredUser;
  };

  const logout = async () => {
    await authRepo.logout();
    setUser(null);
  };

  const updateProfile = async (updates) => {
    const updated = await authRepo.updateProfile(updates);
    setUser(updated);
    return updated;
  };

  return (
    <AuthContext.Provider value={{ user, loading, login, loginWithGoogle, register, logout, updateProfile }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
