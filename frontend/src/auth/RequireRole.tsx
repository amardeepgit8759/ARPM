import { Navigate, Outlet } from 'react-router-dom';
import type { Role } from '@/api/schemas';
import { homePathFor } from '@/app/navigation';
import { useAuth } from './useAuth';

/**
 * Keeps each role on its own screens. It is a convenience, not the security boundary: the API
 * refuses a student on every admin endpoint regardless of what the browser renders.
 *
 * Sits inside ProtectedRoute, so the session is already known to be authenticated here.
 */
export function RequireRole({ role }: { role: Role }): JSX.Element {
  const { state } = useAuth();

  if (state.status !== 'authenticated') {
    return <Navigate to="/login" replace />;
  }

  if (state.user.role !== role) {
    return <Navigate to={homePathFor(state.user.role)} replace />;
  }

  return <Outlet />;
}
