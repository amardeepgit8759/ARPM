import { describe, expect, it, vi } from 'vitest';
import { api } from './client';
import { ApiContractError, ApiError } from './problem';
import { sessionSchema } from './schemas';
import { tokenStore } from './tokenStore';
import { ADA_SESSION, stubFetch } from '@/test/harness';

describe('api client', () => {
  it('parses a successful response through its schema', async () => {
    stubFetch({ '/api/v1/auth/login': { status: 200, body: ADA_SESSION } });

    const session = await api.requestParsed(sessionSchema, '/api/v1/auth/login', {
      method: 'POST',
      body: { email: 'ada@example.com', password: 'correct-horse-battery' },
      anonymous: true,
    });

    expect(session.user.email).toBe('ada@example.com');
  });

  it('sends cookies so the refresh token travels', async () => {
    stubFetch({ '/api/v1/auth/refresh': { status: 200, body: ADA_SESSION } });

    await api.requestParsed(sessionSchema, '/api/v1/auth/refresh', {
      method: 'POST',
      anonymous: true,
    });

    const init = vi.mocked(fetch).mock.calls[0]![1] as RequestInit;
    expect(init.credentials).toBe('include');
  });

  it('attaches the access token when one is held', async () => {
    tokenStore.set('access-token-for-ada');
    stubFetch({ '/api/v1/me': { status: 200, body: { ok: true } } });

    await api.request('/api/v1/me');

    const init = vi.mocked(fetch).mock.calls[0]![1] as RequestInit;
    expect((init.headers as Record<string, string>).Authorization).toBe(
      'Bearer access-token-for-ada',
    );
  });

  it('omits the access token on anonymous calls', async () => {
    tokenStore.set('access-token-for-ada');
    stubFetch({ '/api/v1/auth/login': { status: 200, body: ADA_SESSION } });

    await api.request('/api/v1/auth/login', { method: 'POST', body: {}, anonymous: true });

    const init = vi.mocked(fetch).mock.calls[0]![1] as RequestInit;
    expect((init.headers as Record<string, string>).Authorization).toBeUndefined();
  });

  it('turns a problem document into an ApiError that keeps the type and field', async () => {
    stubFetch({
      '/api/v1/auth/register': {
        status: 400,
        body: {
          type: 'urn:placefy:problem:validation-failed',
          title: 'Validation failed',
          status: 400,
          detail: 'Password must be at least 12 characters.',
          field: 'password',
        },
      },
    });

    await expect(api.request('/api/v1/auth/register', { method: 'POST', body: {} })).rejects.toSatisfy(
      (error: unknown) =>
        error instanceof ApiError &&
        error.status === 400 &&
        error.problem.field === 'password' &&
        error.is('urn:placefy:problem:validation-failed'),
    );
  });

  it('still produces a readable error when the body is not a problem document', async () => {
    stubFetch({ '/api/v1/me': { status: 502, body: null } });

    await expect(api.request('/api/v1/me')).rejects.toSatisfy(
      (error: unknown) =>
        error instanceof ApiError && error.status === 502 && error.problem.title === 'Unexpected error',
    );
  });

  it('rejects a 200 whose shape does not match the contract', async () => {
    stubFetch({ '/api/v1/auth/login': { status: 200, body: { accessToken: 'only-this' } } });

    await expect(
      api.requestParsed(sessionSchema, '/api/v1/auth/login', { method: 'POST', anonymous: true }),
    ).rejects.toBeInstanceOf(ApiContractError);
  });
});
