import { screen, waitFor } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { App } from '@/App';
import { tokenStore } from '@/api/tokenStore';
import { ADA_SESSION, NO_SESSION, renderApp, stubFetch } from '@/test/harness';

describe('session restoration', () => {
  it('shows a loading state while the session is being restored', () => {
    stubFetch({ '/api/v1/auth/refresh': { status: 200, body: ADA_SESSION } });

    renderApp(<App />, '/dashboard');

    expect(screen.getByText('Restoring your session')).toBeInTheDocument();
  });

  it('restores a signed-in user from the refresh cookie without showing the login screen', async () => {
    stubFetch({
      '/api/v1/auth/refresh': { status: 200, body: ADA_SESSION },
      '/api/v1/students/me': { status: 200, body: ADA_SESSION.user },
    });

    renderApp(<App />, '/dashboard');

    await waitFor(() => expect(screen.getByText('Account')).toBeInTheDocument());
    expect(screen.queryByRole('heading', { name: 'Sign in' })).not.toBeInTheDocument();
    expect(tokenStore.get()).toBe('access-token-for-ada');
  });

  it('sends a visitor with no valid cookie to the login screen', async () => {
    stubFetch({ '/api/v1/auth/refresh': NO_SESSION });

    renderApp(<App />, '/dashboard');

    await waitFor(() =>
      expect(screen.getByRole('heading', { name: 'Sign in' })).toBeInTheDocument(),
    );
    expect(tokenStore.get()).toBeNull();
  });

  it('a failed restore is not surfaced as an error, because it is the ordinary first visit', async () => {
    stubFetch({ '/api/v1/auth/refresh': NO_SESSION });

    renderApp(<App />, '/');

    await waitFor(() =>
      expect(screen.getByRole('heading', { name: 'Sign in' })).toBeInTheDocument(),
    );
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('sends an authenticated visitor at the root straight to the dashboard', async () => {
    stubFetch({
      '/api/v1/auth/refresh': { status: 200, body: ADA_SESSION },
      '/api/v1/students/me': { status: 200, body: ADA_SESSION.user },
    });

    renderApp(<App />, '/');

    await waitFor(() => expect(screen.getByText('Account')).toBeInTheDocument());
  });
});
