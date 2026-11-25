import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { setUnauthenticatedHandler } from 'ghostbridge-ai-frontend';
import { fetchCurrentUser, refreshCsrf, signIn, signOut, type AuthUser } from '../api/authApi';

export type AuthStatus = 'loading' | 'anonymous' | 'authenticated' | 'unavailable';

interface AuthContextValue {
  status: AuthStatus;
  user: AuthUser | null;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  retry: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

/**
 * Holds the signed-in state. The backend session (HttpOnly cookie) is the only credential; nothing is stored in the
 * browser. A 401 from any call (for example an expired session) returns the user to the sign-in screen.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>('loading');
  const [user, setUser] = useState<AuthUser | null>(null);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    setUnauthenticatedHandler(() => {
      setUser(null);
      setStatus((current) => (current === 'authenticated' ? 'anonymous' : current));
    });
    return () => setUnauthenticatedHandler(null);
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    refreshCsrf(controller.signal)
      .then(() => fetchCurrentUser(controller.signal))
      .then((current) => {
        if (controller.signal.aborted) return;
        setUser(current);
        setStatus(current ? 'authenticated' : 'anonymous');
      })
      .catch(() => {
        if (!controller.signal.aborted) setStatus('unavailable');
      });
    return () => controller.abort();
  }, [attempt]);

  const login = useCallback(async (username: string, password: string) => {
    const signedIn = await signIn(username, password);
    setUser(signedIn);
    setStatus('authenticated');
  }, []);

  const logout = useCallback(async () => {
    try {
      await signOut();
    } finally {
      setUser(null);
      setStatus('anonymous');
    }
  }, []);

  const retry = useCallback(() => {
    setStatus('loading');
    setAttempt((value) => value + 1);
  }, []);

  const value = useMemo(() => ({ status, user, login, logout, retry }), [status, user, login, logout, retry]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (context === null) throw new Error('useAuth must be used inside AuthProvider');
  return context;
}
