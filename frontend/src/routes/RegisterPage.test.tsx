import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { App } from '@/App';
import { ADA_SESSION, NO_SESSION, renderApp, stubFetch } from '@/test/harness';

async function arriveAtRegister(): Promise<void> {
  await waitFor(() =>
    expect(screen.getByRole('heading', { name: 'Create your account' })).toBeInTheDocument(),
  );
}

async function fillIn(user: ReturnType<typeof userEvent.setup>, password: string): Promise<void> {
  await user.type(screen.getByLabelText('Full name'), 'Ada Lovelace');
  await user.type(screen.getByLabelText('Email'), 'ada@example.com');
  await user.type(screen.getByLabelText('Password'), password);
}

describe('RegisterPage', () => {
  it('mirrors the server password rule before making a request', async () => {
    const user = userEvent.setup();
    stubFetch({ '/api/v1/auth/refresh': NO_SESSION });
    renderApp(<App />, '/register');
    await arriveAtRegister();

    await fillIn(user, 'short');
    await user.click(screen.getByRole('button', { name: 'Create account' }));

    expect(await screen.findByText('Use at least 12 characters.')).toBeInTheDocument();
  });

  it('rejects a malformed email before making a request', async () => {
    const user = userEvent.setup();
    stubFetch({ '/api/v1/auth/refresh': NO_SESSION });
    renderApp(<App />, '/register');
    await arriveAtRegister();

    await user.type(screen.getByLabelText('Full name'), 'Ada Lovelace');
    await user.type(screen.getByLabelText('Email'), 'not-an-email');
    await user.type(screen.getByLabelText('Password'), 'correct-horse-battery');
    await user.click(screen.getByRole('button', { name: 'Create account' }));

    expect(await screen.findByText('Enter a valid email address.')).toBeInTheDocument();
  });

  it('creates an account and lands on the dashboard', async () => {
    const user = userEvent.setup();
    stubFetch({
      '/api/v1/auth/refresh': NO_SESSION,
      '/api/v1/auth/register': { status: 201, body: ADA_SESSION },
      '/api/v1/me': { status: 200, body: ADA_SESSION.user },
    });

    renderApp(<App />, '/register');
    await arriveAtRegister();

    await fillIn(user, 'correct-horse-battery');
    await user.click(screen.getByRole('button', { name: 'Create account' }));

    await waitFor(() => expect(screen.getByText('Account')).toBeInTheDocument());
  });

  it('puts a duplicate-email conflict on the email field, not in a banner', async () => {
    const user = userEvent.setup();
    stubFetch({
      '/api/v1/auth/refresh': NO_SESSION,
      '/api/v1/auth/register': {
        status: 409,
        body: {
          type: 'urn:placefy:problem:email-already-registered',
          title: 'Email already registered',
          status: 409,
          detail: 'An account with that email already exists.',
        },
      },
    });

    renderApp(<App />, '/register');
    await arriveAtRegister();

    await fillIn(user, 'correct-horse-battery');
    await user.click(screen.getByRole('button', { name: 'Create account' }));

    expect(
      await screen.findByText('An account with that email already exists.'),
    ).toBeInTheDocument();
    expect(screen.getByLabelText('Email')).toHaveAttribute('aria-invalid', 'true');
  });

  it('surfaces a server rule the client does not mirror', async () => {
    const user = userEvent.setup();
    stubFetch({
      '/api/v1/auth/refresh': NO_SESSION,
      '/api/v1/auth/register': {
        status: 400,
        body: {
          type: 'urn:placefy:problem:validation-failed',
          title: 'Validation failed',
          status: 400,
          detail: 'Name is required.',
          field: 'name',
        },
      },
    });

    renderApp(<App />, '/register');
    await arriveAtRegister();

    await fillIn(user, 'correct-horse-battery');
    await user.click(screen.getByRole('button', { name: 'Create account' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Name is required.');
  });
});
