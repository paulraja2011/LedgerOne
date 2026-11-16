import { createContext, useContext, useState, type ReactNode } from 'react';
import { post, tokenStore } from './api/client';
import type { CompanyRef, LoginResponse, User } from './api/types';

interface Session { user: User; company: CompanyRef }
interface AuthState {
  session: Session | null;
  login: (email: string, password: string) => Promise<void>;
  logout: () => void;
}

const SESSION_KEY = 'ledgerone.session';
const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(() => {
    const raw = sessionStorage.getItem(SESSION_KEY);
    return raw && tokenStore.get() ? (JSON.parse(raw) as Session) : null;
  });

  const login = async (email: string, password: string) => {
    const res = await post<LoginResponse>('/auth/login', { email, password });
    tokenStore.set(res.token);
    const s = { user: res.user, company: res.company };
    sessionStorage.setItem(SESSION_KEY, JSON.stringify(s));
    setSession(s);
  };

  const logout = () => {
    tokenStore.clear();
    sessionStorage.removeItem(SESSION_KEY);
    setSession(null);
  };

  return <AuthContext.Provider value={{ session, login, logout }}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth outside AuthProvider');
  return ctx;
}
