import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { App } from '@/App';
import { ADA_SESSION, ADMIN_SESSION, renderApp, stubFetch } from '@/test/harness';
import { ADMIN_DESTINATIONS, STUDENT_DESTINATIONS } from './navigation';

const AS_STUDENT = {
  '/api/v1/auth/refresh': { status: 200, body: ADA_SESSION },
  '/api/v1/students/me': { status: 200, body: ADA_SESSION.user },
};

const AS_ADMIN = {
  '/api/v1/auth/refresh': { status: 200, body: ADMIN_SESSION },
  '/api/v1/students/me': { status: 200, body: ADMIN_SESSION.user },
  '/api/v1/admin/overview': { status: 200, body: { studentCount: 3 } },
};

describe('navigation', () => {
  it('shows a student the eight student entries and no admin entry', async () => {
    stubFetch(AS_STUDENT);
    renderApp(<App />, '/dashboard');

    const nav = await screen.findByRole('navigation', { name: 'Main' });
    const labels = within(nav)
      .getAllByRole('link')
      .map((link) => link.textContent?.replace('Soon', ''));

    expect(labels).toEqual(STUDENT_DESTINATIONS.map((destination) => destination.label));
    expect(screen.queryByRole('navigation', { name: 'Administration' })).not.toBeInTheDocument();
  });

  it('shows an administrator the admin entries and no student entry', async () => {
    stubFetch(AS_ADMIN);
    renderApp(<App />, '/admin');

    const nav = await screen.findByRole('navigation', { name: 'Administration' });
    const labels = within(nav)
      .getAllByRole('link')
      .map((link) => link.textContent?.replace('Soon', ''));

    expect(labels).toEqual(ADMIN_DESTINATIONS.map((destination) => destination.label));
    expect(screen.queryByRole('navigation', { name: 'Main' })).not.toBeInTheDocument();
  });

  it('sends a student who opens an admin address back to their dashboard', async () => {
    stubFetch(AS_STUDENT);
    renderApp(<App />, '/admin/companies');

    expect(await screen.findByRole('heading', { name: 'Dashboard' })).toBeInTheDocument();
  });

  it('sends an administrator who opens a student address back to the admin dashboard', async () => {
    stubFetch(AS_ADMIN);
    renderApp(<App />, '/roadmap');

    expect(await screen.findByRole('heading', { name: 'Admin dashboard' })).toBeInTheDocument();
  });

  it('sends each role to its own home from the root', async () => {
    stubFetch(AS_ADMIN);
    renderApp(<App />, '/');

    expect(await screen.findByRole('heading', { name: 'Admin dashboard' })).toBeInTheDocument();
  });

  it('lets an administrator reach their own profile', async () => {
    const user = userEvent.setup();
    stubFetch(AS_ADMIN);
    renderApp(<App />, '/admin');

    await user.click(await screen.findByRole('link', { name: 'Your profile' }));

    expect(await screen.findByRole('heading', { name: 'Profile' })).toBeInTheDocument();
  });

  it('says plainly that an unbuilt module is not available, with no sample figures', async () => {
    stubFetch(AS_STUDENT);
    renderApp(<App />, '/analysis');

    expect(await screen.findByRole('heading', { name: 'Analysis' })).toBeInTheDocument();
    expect(screen.getByText('Not available yet')).toBeInTheDocument();
    expect(screen.queryByText(/\d+\s*%/)).not.toBeInTheDocument();
  });

  it('opens and closes the menu on narrow screens', async () => {
    const user = userEvent.setup();
    stubFetch(AS_STUDENT);
    renderApp(<App />, '/dashboard');

    const toggle = await screen.findByRole('button', { name: 'Menu' });
    expect(toggle).toHaveAttribute('aria-expanded', 'false');

    await user.click(toggle);
    expect(screen.getByRole('button', { name: 'Close' })).toHaveAttribute('aria-expanded', 'true');

    // Following a link closes the drawer.
    const drawerNav = within(document.getElementById('app-menu')!).getByRole('navigation');
    await user.click(within(drawerNav).getByRole('link', { name: /Profile/ }));

    await waitFor(() => expect(document.getElementById('app-menu')).toBeNull());
    expect(screen.getByRole('heading', { name: 'Profile' })).toBeInTheDocument();
  });
});
