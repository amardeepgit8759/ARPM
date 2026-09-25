import { useEffect, useState } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import type { User } from '@/api/schemas';
import { useAuth } from '@/auth/useAuth';
import { Button } from '@/components/Button';
import { ADMIN_ACCOUNT_DESTINATION, destinationsFor, type Destination } from './navigation';

/**
 * The frame around every signed-in screen: a sidebar on wide screens, a top bar with a drawer on
 * narrow ones. The menu is chosen by role, so a student never sees an admin entry and the
 * reverse.
 */
export function AppShell(): JSX.Element {
  const { state, signOut } = useAuth();
  const [menuOpen, setMenuOpen] = useState(false);
  const [signingOut, setSigningOut] = useState(false);
  const location = useLocation();

  // A tap on a menu entry navigates; the drawer should not stay open over the new page.
  useEffect(() => setMenuOpen(false), [location.pathname]);

  if (state.status !== 'authenticated') {
    return <Outlet />;
  }

  const { user } = state;

  async function handleSignOut(): Promise<void> {
    setSigningOut(true);
    await signOut();
  }

  const sidebar = (
    <Sidebar user={user} signingOut={signingOut} onSignOut={() => void handleSignOut()} />
  );

  return (
    <div className="min-h-screen lg:flex">
      <header className="border-border bg-surface sticky top-0 z-20 flex items-center justify-between border-b px-4 py-3 lg:hidden">
        <Brand role={user.role} />
        <button
          type="button"
          onClick={() => setMenuOpen((open) => !open)}
          aria-expanded={menuOpen}
          aria-controls="app-menu"
          className="text-ink border-border-strong hover:bg-canvas rounded border px-3 py-1.5 text-sm"
        >
          {menuOpen ? 'Close' : 'Menu'}
        </button>
      </header>

      {menuOpen ? (
        <div className="fixed inset-0 z-30 lg:hidden">
          <button
            type="button"
            aria-label="Close menu"
            className="bg-ink/30 absolute inset-0"
            onClick={() => setMenuOpen(false)}
          />
          <div id="app-menu" className="bg-surface relative h-full w-64 max-w-[80vw] overflow-y-auto shadow-lg">
            {sidebar}
          </div>
        </div>
      ) : null}

      <aside className="border-border bg-surface sticky top-0 hidden h-screen w-60 shrink-0 overflow-y-auto border-r lg:block">
        {sidebar}
      </aside>

      <main className="min-w-0 flex-1">
        <div className="mx-auto flex max-w-5xl flex-col gap-6 px-4 py-6 sm:px-6 lg:py-8">
          <Outlet />
        </div>
      </main>
    </div>
  );
}

function Sidebar({
  user,
  signingOut,
  onSignOut,
}: {
  user: User;
  signingOut: boolean;
  onSignOut: () => void;
}): JSX.Element {
  const destinations = destinationsFor(user.role);

  return (
    <div className="flex h-full flex-col">
      <div className="hidden px-4 pt-5 pb-3 lg:block">
        <Brand role={user.role} />
      </div>

      <nav aria-label={user.role === 'ADMIN' ? 'Administration' : 'Main'} className="flex-1 px-2 py-2">
        <ul className="flex flex-col gap-0.5">
          {destinations.map((destination) => (
            <li key={destination.to}>
              <MenuLink destination={destination} />
            </li>
          ))}
        </ul>
      </nav>

      <div className="border-border flex flex-col gap-3 border-t px-4 py-4">
        <div className="min-w-0">
          <p className="text-ink truncate text-sm font-medium">{user.name}</p>
          <p className="text-ink-subtle truncate text-xs">{user.email}</p>
        </div>
        {user.role === 'ADMIN' ? <MenuLink destination={ADMIN_ACCOUNT_DESTINATION} /> : null}
        <Button variant="secondary" onClick={onSignOut} loading={signingOut}>
          {signingOut ? 'Signing out' : 'Sign out'}
        </Button>
      </div>
    </div>
  );
}

function MenuLink({ destination }: { destination: Destination }): JSX.Element {
  return (
    <NavLink
      to={destination.to}
      // "/admin" is a prefix of every admin path; without `end` it would stay highlighted everywhere.
      end
      className={({ isActive }) =>
        [
          'flex items-center justify-between gap-2 rounded px-3 py-2 text-sm',
          isActive ? 'bg-accent-subtle text-accent font-medium' : 'text-ink-muted hover:bg-canvas hover:text-ink',
        ].join(' ')
      }
    >
      <span>{destination.label}</span>
      {destination.pending ? (
        <span className="text-ink-subtle text-[11px] font-normal">Soon</span>
      ) : null}
    </NavLink>
  );
}

function Brand({ role }: { role: User['role'] }): JSX.Element {
  return (
    <div className="flex items-baseline gap-2">
      <span className="text-ink text-base font-semibold tracking-tight">APRM</span>
      {role === 'ADMIN' ? (
        <span className="text-ink-subtle text-xs font-medium tracking-wide uppercase">Admin</span>
      ) : null}
    </div>
  );
}
