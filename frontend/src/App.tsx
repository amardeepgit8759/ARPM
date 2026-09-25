import { Navigate, Route, Routes } from 'react-router-dom';
import { AppShell } from '@/app/AppShell';
import { ADMIN_DESTINATIONS, STUDENT_DESTINATIONS, homePathFor, type Destination } from '@/app/navigation';
import { ProtectedRoute } from '@/auth/ProtectedRoute';
import { RequireRole } from '@/auth/RequireRole';
import { useAuth } from '@/auth/useAuth';
import { FullPageLoader } from '@/components/FullPageLoader';
import { AdminDashboardPage } from '@/routes/admin/AdminDashboardPage';
import { DashboardPage } from '@/routes/DashboardPage';
import { LoginPage } from '@/routes/LoginPage';
import { ModuleUnavailablePage } from '@/routes/ModuleUnavailablePage';
import { NotFoundPage } from '@/routes/NotFoundPage';
import { ProfilePage } from '@/routes/ProfilePage';
import { RegisterPage } from '@/routes/RegisterPage';

export function App(): JSX.Element {
  return (
    <Routes>
      <Route path="/" element={<Landing />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />

      <Route element={<ProtectedRoute />}>
        <Route element={<AppShell />}>
          <Route path="/profile" element={<ProfilePage />} />

          <Route element={<RequireRole role="STUDENT" />}>
            <Route path="/dashboard" element={<DashboardPage />} />
            {pendingRoutes(STUDENT_DESTINATIONS)}
          </Route>

          <Route element={<RequireRole role="ADMIN" />}>
            <Route path="/admin" element={<AdminDashboardPage />} />
            {pendingRoutes(ADMIN_DESTINATIONS)}
          </Route>
        </Route>
      </Route>

      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}

/** Routes for menu entries whose module is not built yet. Each says so, and nothing more. */
function pendingRoutes(destinations: readonly Destination[]): JSX.Element[] {
  return destinations
    .filter((destination) => destination.pending !== undefined)
    .map((destination) => (
      <Route
        key={destination.to}
        path={destination.to}
        element={<ModuleUnavailablePage title={destination.label} description={destination.pending!} />}
      />
    ));
}

/** Sends the visitor wherever their session says they belong, once it is known. */
function Landing(): JSX.Element {
  const { state } = useAuth();

  if (state.status === 'restoring') {
    return <FullPageLoader label="Loading APRM" />;
  }

  return (
    <Navigate to={state.status === 'authenticated' ? homePathFor(state.user.role) : '/login'} replace />
  );
}
