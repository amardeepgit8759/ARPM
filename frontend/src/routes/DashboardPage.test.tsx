import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { App } from '@/App';
import { tokenStore } from '@/api/tokenStore';
import { ADA_SESSION, fetchCalls, renderApp, stubFetch } from '@/test/harness';

const SIGNED_IN = { '/api/v1/auth/refresh': { status: 200, body: ADA_SESSION } };

describe('DashboardPage', () => {
  it('shows a loading state while the profile is being fetched', async () => {
    stubFetch({
      ...SIGNED_IN,
      '/api/v1/students/me': { status: 200, body: ADA_SESSION.user },
    });

    renderApp(<App />, '/dashboard');

    expect(await screen.findByText('Loading your profile')).toBeInTheDocument();
  });

  it('shows the profile once it loads', async () => {
    stubFetch({ ...SIGNED_IN, '/api/v1/students/me': { status: 200, body: ADA_SESSION.user } });

    renderApp(<App />, '/dashboard');

    expect(await screen.findByText('STUDENT')).toBeInTheDocument();
    expect(screen.getAllByText('Ada Lovelace').length).toBeGreaterThan(0);
    expect(screen.getByText('STUDENT')).toBeInTheDocument();
    expect(screen.getByText('2026-01-15')).toBeInTheDocument();
  });

  it('shows an error with a retry when the profile cannot be loaded', async () => {
    const user = userEvent.setup();
    let attempts = 0;

    stubFetch({
      ...SIGNED_IN,
      // The query retries twice before giving up, so the failure has to outlast all three
      // attempts for the error state to be reached at all.
      '/api/v1/students/me': () => {
        attempts += 1;
        return attempts <= 3
          ? { status: 500, body: null }
          : { status: 200, body: ADA_SESSION.user };
      },
    });

    renderApp(<App />, '/dashboard');

    expect(await screen.findByRole('alert')).toHaveTextContent('Could not load your profile');

    await user.click(screen.getByRole('button', { name: 'Try again' }));

    await waitFor(() => expect(screen.getByText('STUDENT')).toBeInTheDocument());
  });

  it('states that there is no assessment yet rather than showing a zero', async () => {
    stubFetch({ ...SIGNED_IN, '/api/v1/students/me': { status: 200, body: ADA_SESSION.user } });

    renderApp(<App />, '/dashboard');

    expect(await screen.findByText('No assessment yet')).toBeInTheDocument();
    // A readiness figure must trace to a persisted scoring run. None exists, so none is drawn.
    expect(screen.queryByText('0')).not.toBeInTheDocument();
    expect(screen.queryByText('0%')).not.toBeInTheDocument();
  });

  it('signing out revokes the session on the server and returns to the login screen', async () => {
    const user = userEvent.setup();
    stubFetch({
      ...SIGNED_IN,
      '/api/v1/students/me': { status: 200, body: ADA_SESSION.user },
      '/api/v1/auth/logout': { status: 204, body: null },
    });

    renderApp(<App />, '/dashboard');
    await screen.findByText('STUDENT');

    await user.click(screen.getByRole('button', { name: 'Sign out' }));

    await waitFor(() =>
      expect(screen.getByRole('heading', { name: 'Sign in' })).toBeInTheDocument(),
    );
    expect(fetchCalls()).toContain('POST /api/v1/auth/logout');
    expect(tokenStore.get()).toBeNull();
  });

  it('still signs out locally when the server cannot be reached', async () => {
    const user = userEvent.setup();
    stubFetch({
      ...SIGNED_IN,
      '/api/v1/students/me': { status: 200, body: ADA_SESSION.user },
      '/api/v1/auth/logout': { status: 503, body: null },
    });

    renderApp(<App />, '/dashboard');
    await screen.findByText('STUDENT');

    await user.click(screen.getByRole('button', { name: 'Sign out' }));

    await waitFor(() =>
      expect(screen.getByRole('heading', { name: 'Sign in' })).toBeInTheDocument(),
    );
    expect(tokenStore.get()).toBeNull();
  });
});
