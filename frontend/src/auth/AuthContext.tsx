import { createContext, useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { authApi, type LoginInput, type RegisterInput } from '@/api/auth';
import { api } from '@/api/client';
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
  /** Revokes the session on the server, then forgets it here. Never fails from the user's side. */
  signOut: () => Promise<void>;
  /** Adopts a session the server issued outside login, e.g. after a password change. */
  adoptSession: (session: Session) => void;
  /** Keeps the signed-in user in step after they edit their own profile. */
  updateUser: (user: User) => void;
  /** Forgets the session locally when the account no longer exists. */
  endSession: () => void;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }): JSX.Element {
  const [state, setState] = useState<AuthState>({ status: 'restoring' });
  const queryClient = useQueryClient();

  const adopt = useCallback((session: Session): void => {
    tokenStore.set(session.accessToken);
    setState({ status: 'authenticated', user: session.user });
  }, []);

  const endSession = useCallback((): void => {
    tokenStore.clear();
    // Cached reads belong to the user who made them; the next person on this browser must
    // never see them, even for a frame.
    queryClient.clear();
    setState({ status: 'anonymous' });
  }, [queryClient]);

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

  // The client renews an expired access token by itself. If that renewal fails, the session is
  // over, and the screen has to say so rather than keep showing a signed-in user.
  useEffect(
    () =>
      api.onSessionChange((session) => {
        if (session === null) {
          endSession();
        } else {
          setState({ status: 'authenticated', user: session.user });
        }
      }),
    [endSession],
  );

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

  const signOut = useCallback(async (): Promise<void> => {
    try {
      await authApi.logout();
    } catch {
      // The server could not be reached. Signing out locally still has to work; the refresh
      // cookie then lives until it expires, which is no worse than before logout existed.
    } finally {
      endSession();
    }
  }, [endSession]);

  const updateUser = useCallback((user: User): void => {
    setState((current) => (current.status === 'authenticated' ? { status: 'authenticated', user } : current));
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({ state, register, login, signOut, adoptSession: adopt, updateUser, endSession }),
    [state, register, login, signOut, adopt, updateUser, endSession],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
