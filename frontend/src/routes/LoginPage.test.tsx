import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { App } from '@/App';
import { tokenStore } from '@/api/tokenStore';
import { ADA_SESSION, NO_SESSION, renderApp, stubFetch } from '@/test/harness';

const INVALID_CREDENTIALS = {
  status: 401,
  body: {
    type: 'urn:placefy:problem:invalid-credentials',
    title: 'Invalid credentials',
    status: 401,
    detail: 'Email or password is incorrect.',
  },
};

async function arriveAtLogin(): Promise<void> {
  await waitFor(() => expect(screen.getByRole('heading', { name: 'Sign in' })).toBeInTheDocument());
}

describe('LoginPage', () => {
  it('validates presence before calling the API', async () => {
    const user = userEvent.setup();
    stubFetch({ '/api/v1/auth/refresh': NO_SESSION });
    renderApp(<App />, '/login');
    await arriveAtLogin();

    await user.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByText('Enter your email address.')).toBeInTheDocument();
    expect(screen.getByText('Enter your password.')).toBeInTheDocument();
  });

  it('shows a busy button while the request is in flight', async () => {
    const user = userEvent.setup();
    let release!: () => void;
    const held = new Promise<void>((resolve) => {
      release = resolve;
    });

    stubFetch({
      '/api/v1/auth/refresh': NO_SESSION,
      '/api/v1/auth/login': () => {
        void held;
        return { status: 200, body: ADA_SESSION };
      },
    });

    renderApp(<App />, '/login');
    await arriveAtLogin();

    await user.type(screen.getByLabelText('Email'), 'ada@example.com');
    await user.type(screen.getByLabelText('Password'), 'correct-horse-battery');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));

    await waitFor(() => expect(screen.getByText('Account')).toBeInTheDocument());
    release();
  });

  it('signs in and lands on the dashboard', async () => {
    const user = userEvent.setup();
    stubFetch({
      '/api/v1/auth/refresh': NO_SESSION,
      '/api/v1/auth/login': { status: 200, body: ADA_SESSION },
      '/api/v1/me': { status: 200, body: ADA_SESSION.user },
    });

    renderApp(<App />, '/login');
    await arriveAtLogin();

    await user.type(screen.getByLabelText('Email'), 'ada@example.com');
    await user.type(screen.getByLabelText('Password'), 'correct-horse-battery');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));

    await waitFor(() => expect(screen.getByText('Account')).toBeInTheDocument());
    expect(tokenStore.get()).toBe('access-token-for-ada');
  });

  it('reports a rejected sign-in without revealing whether the account exists', async () => {
    const user = userEvent.setup();
    stubFetch({
      '/api/v1/auth/refresh': NO_SESSION,
      '/api/v1/auth/login': INVALID_CREDENTIALS,
    });

    renderApp(<App />, '/login');
    await arriveAtLogin();

    await user.type(screen.getByLabelText('Email'), 'nobody@example.com');
    await user.type(screen.getByLabelText('Password'), 'whatever-they-typed');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Email or password is incorrect.');
    expect(alert).not.toHaveTextContent(/no account|not registered|unknown/i);
    expect(tokenStore.get()).toBeNull();
  });

  it('reports a network failure as something the user can act on', async () => {
    const user = userEvent.setup();
    stubFetch({ '/api/v1/auth/refresh': NO_SESSION });
    renderApp(<App />, '/login');
    await arriveAtLogin();

    await user.type(screen.getByLabelText('Email'), 'ada@example.com');
    await user.type(screen.getByLabelText('Password'), 'correct-horse-battery');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Could not reach APRM');
  });
});
