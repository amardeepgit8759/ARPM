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

export const ADMIN: User = {
  id: '7c1e4b2a-9f3d-4e6a-8b5c-2d1f0a9e8c74',
  name: 'Site Admin',
  email: 'admin@example.com',
  role: 'ADMIN',
  createdAt: '2026-01-10T09:00:00Z',
};

export const ADMIN_SESSION: Session = {
  accessToken: 'access-token-for-admin',
  accessTokenExpiresAt: '2026-01-15T10:15:00Z',
  user: ADMIN,
};

export const ADA_SESSION: Session = {
  accessToken: 'access-token-for-ada',
  accessTokenExpiresAt: '2026-01-15T10:15:00Z',
  user: ADA,
};

export interface StubbedResponse {
  status: number;
  body: unknown;
}

type Route = StubbedResponse | ((init: RequestInit) => StubbedResponse);

/**
 * Stubs fetch per path so tests assert against the real client — headers, credentials, problem
 * parsing and schema validation all run. Mocking the api module instead would leave exactly
 * that layer untested.
 *
 * A key is either a path ("/api/v1/students/me"), matching any method, or a method and a path
 * ("PUT /api/v1/students/me"), which wins over the bare path for that method.
 */
export function stubFetch(routes: Record<string, Route>): void {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL, init: RequestInit = {}) => {
      const url = typeof input === 'string' ? input : input.toString();
      const method = (init.method ?? 'GET').toUpperCase();
      const keys = Object.keys(routes);
      const match =
        keys.find((key) => key.startsWith(`${method} `) && url.endsWith(key.slice(method.length + 1))) ??
        keys.find((key) => !key.includes(' ') && url.endsWith(key));

      if (match === undefined) {
        throw new TypeError(`No stub for ${method} ${url}`);
      }

      const route = routes[match]!;
      const { status, body } = typeof route === 'function' ? route(init) : route;

      // A 204 must have no body at all; the Response constructor rejects even an empty string.
      const payload = status === 204 || body === null ? null : JSON.stringify(body);
      return new Response(payload, {
        status,
        headers: { 'Content-Type': status >= 400 ? 'application/problem+json' : 'application/json' },
      });
    }),
  );
}

/** Every call the stubbed fetch received, as "METHOD /path", in order. */
export function fetchCalls(): string[] {
  return vi.mocked(fetch).mock.calls.map(([input, init]) => {
    const url = new URL(typeof input === 'string' ? input : input.toString());
    return `${(init?.method ?? 'GET').toUpperCase()} ${url.pathname}`;
  });
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
