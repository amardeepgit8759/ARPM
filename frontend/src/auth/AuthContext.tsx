import { createContext, useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { authApi, type LoginInput, type RegisterInput } from '@/api/auth';
import { tokenStore } from '@/api/tokenStore';
import type { Session, User } from '@/api/schemas';

/**
 * `restoring` is a real state, not a detail.
 *
 * The access token lives in memory only, so on every page load the app genuinely does not yet
 * know whether the visitor is signed in — it has to ask /auth/refresh. Collapsing that into
 * "anonymous" would flash the login screen at signed-in users on every reload; treating it as
 * its own state lets the router wait instead.
 */
export type AuthState =
  | { status: 'restoring' }
  | { status: 'anonymous' }
  | { status: 'authenticated'; user: User };

export interface AuthContextValue {
  state: AuthState;
  register: (input: RegisterInput) => Promise<void>;
  login: (input: LoginInput) => Promise<void>;
  signOut: () => void;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }): JSX.Element {
  const [state, setState] = useState<AuthState>({ status: 'restoring' });

  const adopt = useCallback((session: Session): void => {
    tokenStore.set(session.accessToken);
    setState({ status: 'authenticated', user: session.user });
  }, []);

  useEffect(() => {
    let cancelled = false;

    authApi
      .refresh()
      .then((session) => {
        if (!cancelled) {
          adopt(session);
        }
      })
      .catch(() => {
        // No cookie, or one the server rejected. Either way the visitor is a stranger; this is
        // the ordinary first-visit path, not an error worth surfacing.
        if (!cancelled) {
          tokenStore.clear();
          setState({ status: 'anonymous' });
        }
      });

    return () => {
      cancelled = true;
    };
  }, [adopt]);

  const register = useCallback(
    async (input: RegisterInput): Promise<void> => {
      adopt(await authApi.register(input));
    },
    [adopt],
  );

  const login = useCallback(
    async (input: LoginInput): Promise<void> => {
      adopt(await authApi.login(input));
    },
    [adopt],
  );

  const signOut = useCallback((): void => {
    // Clears this tab only. Server-side revocation needs a logout endpoint, which Phase 0
    // does not have; the refresh cookie therefore stays valid until it expires or is rotated.
    tokenStore.clear();
    setState({ status: 'anonymous' });
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({ state, register, login, signOut }),
    [state, register, login, signOut],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
