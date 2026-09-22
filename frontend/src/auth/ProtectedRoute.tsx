import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { FullPageLoader } from '@/components/FullPageLoader';
import { useAuth } from './useAuth';

export function ProtectedRoute(): JSX.Element {
  const { state } = useAuth();
  const location = useLocation();

  // Waiting, not anonymous: redirecting here would bounce a signed-in user to the login screen
  // on every reload, before the session has had a chance to restore.
  if (state.status === 'restoring') {
    return <FullPageLoader label="Restoring your session" />;
  }

  if (state.status === 'anonymous') {
    // `from` lets the login screen return the user to where they were headed.
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  return <Outlet />;
}
