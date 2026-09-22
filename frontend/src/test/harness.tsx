import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, type RenderResult } from '@testing-library/react';
import type { ReactNode } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { vi } from 'vitest';
import { AuthProvider } from '@/auth/AuthContext';
import type { Session, User } from '@/api/schemas';

export const ADA: User = {
  id: '0f2a6d5e-1c3b-4f8a-9d2e-7b5c1a4e8f30',
  name: 'Ada Lovelace',
  email: 'ada@example.com',
  role: 'STUDENT',
  createdAt: '2026-01-15T10:00:00Z',
};

export const ADA_SESSION: Session = {
  accessToken: 'access-token-for-ada',
  accessTokenExpiresAt: '2026-01-15T10:15:00Z',
  user: ADA,
};

interface StubbedResponse {
  status: number;
  body: unknown;
}

/**
 * Stubs fetch per path so tests assert against the real client — headers, credentials, problem
 * parsing and schema validation all run. Mocking the api module instead would leave exactly
 * that layer untested.
 */
export function stubFetch(routes: Record<string, StubbedResponse | (() => StubbedResponse)>): void {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL) => {
      const url = typeof input === 'string' ? input : input.toString();
      const match = Object.keys(routes).find((path) => url.endsWith(path));

      if (match === undefined) {
        throw new TypeError(`No stub for ${url}`);
      }

      const route = routes[match]!;
      const { status, body } = typeof route === 'function' ? route() : route;

      return new Response(body === null ? '' : JSON.stringify(body), {
        status,
        headers: { 'Content-Type': status >= 400 ? 'application/problem+json' : 'application/json' },
      });
    }),
  );
}

/** A refresh that fails, i.e. an ordinary visitor with no session cookie. */
export const NO_SESSION: StubbedResponse = {
  status: 401,
  body: {
    type: 'urn:placefy:problem:invalid-refresh-token',
    title: 'Session expired',
    status: 401,
    detail: 'Refresh token is missing, expired or already used.',
  },
};

export function renderApp(ui: ReactNode, initialPath = '/'): RenderResult {
  const queryClient = new QueryClient({
    // retryDelay 0, not retry:false, because a component that sets its own `retry` overrides the
    // default, and the real backoff would make those tests take seconds.
    defaultOptions: { queries: { retry: false, retryDelay: 0 }, mutations: { retry: false } },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[initialPath]}>
        <AuthProvider>{ui}</AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}
