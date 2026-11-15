import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { refreshSession, session } from '../api/client';
import { authApi, logoutCurrentSession, type SignupInput } from '../api/endpoints';
import type { User } from '../api/types';

type AuthState =
  { status: 'loading'; user: null } | { status: 'anonymous'; user: null } | { status: 'authenticated'; user: User };

interface AuthContextValue {
  state: AuthState;
  user: User | null;
  login: (email: string, password: string) => Promise<User>;
  signup: (input: SignupInput) => Promise<User>;
  logout: () => Promise<void>;
  setUser: (user: User) => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>(() =>
    session.mayHaveSession ? { status: 'loading', user: null } : { status: 'anonymous', user: null },
  );

  useEffect(() => {
    const unsubscribe = session.subscribe((auth) => {
      setState(auth ? { status: 'authenticated', user: auth.user } : { status: 'anonymous', user: null });
    });
    if (session.mayHaveSession && !session.accessToken) {
      refreshSession().then((auth) => {
        if (!auth) {
          setState({ status: 'anonymous', user: null });
        }
      });
    }
    return () => {
      unsubscribe();
    };
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const auth = await authApi.login(email, password);
    session.set(auth);
    return auth.user;
  }, []);

  const signup = useCallback(async (input: SignupInput) => {
    const auth = await authApi.signup(input);
    session.set(auth);
    return auth.user;
  }, []);

  const logout = useCallback(() => logoutCurrentSession(), []);

  const setUser = useCallback((user: User) => setState({ status: 'authenticated', user }), []);

  const value = useMemo<AuthContextValue>(
    () => ({ state, user: state.user, login, signup, logout, setUser }),
    [state, login, signup, logout, setUser],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside AuthProvider');
  }
  return context;
}

export function useCurrentUser() {
  const { user } = useAuth();
  if (!user) {
    throw new Error('useCurrentUser requires an authenticated user');
  }
  return user;
}
