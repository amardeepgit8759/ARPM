import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { App } from '@/App';
import { tokenStore } from '@/api/tokenStore';
import * as files from '@/lib/saveFile';
import { ADA, ADA_SESSION, fetchCalls, renderApp, stubFetch } from '@/test/harness';

const SIGNED_IN = {
  '/api/v1/auth/refresh': { status: 200, body: ADA_SESSION },
  'GET /api/v1/students/me': { status: 200, body: ADA },
};

const INVALID_CREDENTIALS = {
  status: 401,
  body: {
    type: 'urn:placefy:problem:invalid-credentials',
    title: 'Invalid credentials',
    status: 401,
    detail: 'Email or password is incorrect.',
  },
};

async function openProfile(routes: Parameters<typeof stubFetch>[0]): Promise<ReturnType<typeof userEvent.setup>> {
  const user = userEvent.setup();
  stubFetch({ ...SIGNED_IN, ...routes });
  renderApp(<App />, '/profile');
  await screen.findByRole('button', { name: 'Save details' });
  return user;
}

describe('ProfilePage', () => {
  it('shows a loading state, then the account', async () => {
    stubFetch(SIGNED_IN);
    renderApp(<App />, '/profile');

    expect(await screen.findByText('Loading your profile')).toBeInTheDocument();
    expect(await screen.findByLabelText('Name')).toHaveValue('Ada Lovelace');
    expect(screen.getByLabelText('Email')).toHaveValue('ada@example.com');
    expect(screen.getByLabelText('Email')).toBeDisabled();
    expect(screen.getByText('Student')).toBeInTheDocument();
  });

  it('shows an error with a retry when the profile cannot be loaded', async () => {
    const user = userEvent.setup();
    let attempts = 0;
    stubFetch({
      ...SIGNED_IN,
      'GET /api/v1/students/me': () => {
        attempts += 1;
        return attempts <= 3 ? { status: 500, body: null } : { status: 200, body: ADA };
      },
    });
    renderApp(<App />, '/profile');

    expect(await screen.findByRole('alert')).toHaveTextContent('Could not load your profile');
    await user.click(screen.getByRole('button', { name: 'Try again' }));

    expect(await screen.findByLabelText('Name')).toHaveValue('Ada Lovelace');
  });

  describe('details', () => {
    it('saves a new name and shows it in the menu straight away', async () => {
      let sent: unknown;
      const user = await openProfile({
        'PUT /api/v1/students/me': (init) => {
          sent = JSON.parse(init.body as string);
          return { status: 200, body: { ...ADA, name: 'Ada King' } };
        },
      });

      await user.clear(screen.getByLabelText('Name'));
      await user.type(screen.getByLabelText('Name'), 'Ada King');
      await user.click(screen.getByRole('button', { name: 'Save details' }));

      expect(await screen.findByText('Your details were saved.')).toBeInTheDocument();
      expect(sent).toEqual({ name: 'Ada King' });
      expect(screen.getAllByText('Ada King').length).toBeGreaterThan(0);
    });

    it('does not submit a name that is too short', async () => {
      const user = await openProfile({});

      await user.clear(screen.getByLabelText('Name'));
      await user.type(screen.getByLabelText('Name'), 'A');
      await user.click(screen.getByRole('button', { name: 'Save details' }));

      expect(await screen.findByText('Enter your full name.')).toBeInTheDocument();
      expect(fetchCalls()).not.toContain('PUT /api/v1/students/me');
    });

    it('explains a server-side failure', async () => {
      const user = await openProfile({ 'PUT /api/v1/students/me': { status: 500, body: null } });

      await user.clear(screen.getByLabelText('Name'));
      await user.type(screen.getByLabelText('Name'), 'Ada King');
      await user.click(screen.getByRole('button', { name: 'Save details' }));

      expect(await screen.findByRole('alert')).toHaveTextContent('Could not save your details');
    });
  });

  describe('password', () => {
    async function fillPassword(
      user: ReturnType<typeof userEvent.setup>,
      current: string,
      next: string,
      repeat = next,
    ): Promise<void> {
      await user.type(screen.getByLabelText('Current password'), current);
      await user.type(screen.getByLabelText('New password'), next);
      await user.type(screen.getByLabelText('Repeat new password'), repeat);
      await user.click(screen.getByRole('button', { name: 'Change password' }));
    }

    it('changes the password and adopts the new session', async () => {
      const user = await openProfile({
        '/api/v1/students/me/password': {
          status: 200,
          body: { ...ADA_SESSION, accessToken: 'access-token-after-change' },
        },
      });

      await fillPassword(user, 'correct-horse-battery', 'a-brand-new-passphrase');

      expect(await screen.findByText(/Your password was changed/)).toBeInTheDocument();
      expect(tokenStore.get()).toBe('access-token-after-change');
      expect(screen.getByLabelText('Current password')).toHaveValue('');
    });

    it('marks the current-password field when it is wrong, and stays signed in', async () => {
      const user = await openProfile({ '/api/v1/students/me/password': INVALID_CREDENTIALS });

      await fillPassword(user, 'not-my-password', 'a-brand-new-passphrase');

      expect(await screen.findByText('That is not your current password.')).toBeInTheDocument();
      expect(screen.getByRole('heading', { name: 'Profile' })).toBeInTheDocument();
      expect(fetchCalls()).not.toContain('POST /api/v1/auth/logout');
    });

    it('checks the repeat and the length before sending anything', async () => {
      const user = await openProfile({});

      await fillPassword(user, 'correct-horse-battery', 'short', 'different');

      expect(await screen.findByText('Use at least 12 characters.')).toBeInTheDocument();
      expect(screen.getByText('The two new passwords do not match.')).toBeInTheDocument();
      expect(fetchCalls()).not.toContain('POST /api/v1/students/me/password');
    });
  });

  describe('data export', () => {
    it('downloads the export as a file', async () => {
      const save = vi.spyOn(files, 'saveFile').mockImplementation(() => {});
      const user = await openProfile({
        '/api/v1/students/me/export': { status: 200, body: { formatVersion: '1' } },
      });

      await user.click(screen.getByRole('button', { name: 'Download my data' }));

      expect(await screen.findByText('Your download has started.')).toBeInTheDocument();
      // Not `expect.any(Blob)`: fetch's Blob and the test DOM's Blob come from different realms.
      expect(save).toHaveBeenCalledTimes(1);
      const [blob, filename] = save.mock.calls[0]!;
      expect(filename).toBe('aprm-export.json');
      expect(await blob.text()).toBe('{"formatVersion":"1"}');
    });

    it('explains a failed export', async () => {
      const user = await openProfile({ '/api/v1/students/me/export': { status: 503, body: null } });

      await user.click(screen.getByRole('button', { name: 'Download my data' }));

      expect(await screen.findByRole('alert')).toHaveTextContent('Could not prepare your download');
    });
  });

  describe('deletion', () => {
    it('asks for the password, deletes, and signs the user out', async () => {
      let sent: unknown;
      const user = await openProfile({
        'DELETE /api/v1/students/me': (init) => {
          sent = JSON.parse(init.body as string);
          return { status: 204, body: null };
        },
      });

      await user.click(screen.getByRole('button', { name: 'Delete my account' }));
      await user.type(screen.getByLabelText('Confirm with your password'), 'correct-horse-battery');
      await user.click(screen.getByRole('button', { name: 'Delete permanently' }));

      await waitFor(() => expect(screen.getByRole('heading', { name: 'Sign in' })).toBeInTheDocument());
      expect(sent).toEqual({ password: 'correct-horse-battery' });
      expect(tokenStore.get()).toBeNull();
    });

    it('keeps the account when the password is wrong', async () => {
      const user = await openProfile({ 'DELETE /api/v1/students/me': INVALID_CREDENTIALS });

      await user.click(screen.getByRole('button', { name: 'Delete my account' }));
      await user.type(screen.getByLabelText('Confirm with your password'), 'wrong-password');
      await user.click(screen.getByRole('button', { name: 'Delete permanently' }));

      expect(await screen.findByText('That password is not correct.')).toBeInTheDocument();
      expect(screen.getByRole('heading', { name: 'Profile' })).toBeInTheDocument();
    });

    it('can be cancelled without sending anything', async () => {
      const user = await openProfile({});

      await user.click(screen.getByRole('button', { name: 'Delete my account' }));
      await user.click(screen.getByRole('button', { name: 'Cancel' }));

      expect(screen.getByRole('button', { name: 'Delete my account' })).toBeInTheDocument();
      expect(fetchCalls()).not.toContain('DELETE /api/v1/students/me');
    });
  });
});
