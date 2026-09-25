import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { App } from '@/App';
import { ADMIN_SESSION, renderApp, stubFetch } from '@/test/harness';

const AS_ADMIN = { '/api/v1/auth/refresh': { status: 200, body: ADMIN_SESSION } };

describe('AdminDashboardPage', () => {
  it('shows a loading state, then the live student count', async () => {
    stubFetch({ ...AS_ADMIN, '/api/v1/admin/overview': { status: 200, body: { studentCount: 42 } } });
    renderApp(<App />, '/admin');

    expect(await screen.findByText('Loading platform totals')).toBeInTheDocument();
    expect(await screen.findByText('42')).toBeInTheDocument();
    expect(screen.getByText('registered students')).toBeInTheDocument();
  });

  it('says there are no students rather than drawing a zero', async () => {
    stubFetch({ ...AS_ADMIN, '/api/v1/admin/overview': { status: 200, body: { studentCount: 0 } } });
    renderApp(<App />, '/admin');

    expect(await screen.findByText('No students yet')).toBeInTheDocument();
    expect(screen.queryByText('0')).not.toBeInTheDocument();
  });

  it('shows an error with a retry', async () => {
    const user = userEvent.setup();
    let attempts = 0;
    stubFetch({
      ...AS_ADMIN,
      '/api/v1/admin/overview': () => {
        attempts += 1;
        return attempts <= 3 ? { status: 500, body: null } : { status: 200, body: { studentCount: 1 } };
      },
    });
    renderApp(<App />, '/admin');

    expect(await screen.findByRole('alert')).toHaveTextContent('Could not load platform totals');
    await user.click(screen.getByRole('button', { name: 'Try again' }));

    expect(await screen.findByText('registered student')).toBeInTheDocument();
  });

  it('shows the refusal without retrying when the server says the caller is not an admin', async () => {
    let attempts = 0;
    stubFetch({
      ...AS_ADMIN,
      '/api/v1/admin/overview': () => {
        attempts += 1;
        return {
          status: 403,
          body: {
            type: 'urn:placefy:problem:forbidden',
            title: 'Forbidden',
            status: 403,
            detail: 'This resource is available to administrators only.',
          },
        };
      },
    });
    renderApp(<App />, '/admin');

    expect(await screen.findByRole('alert')).toHaveTextContent('administrators only');
    expect(attempts).toBe(1);
  });
});
