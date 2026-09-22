import { Navigate, Route, Routes } from 'react-router-dom';
import { ProtectedRoute } from '@/auth/ProtectedRoute';
import { useAuth } from '@/auth/useAuth';
import { FullPageLoader } from '@/components/FullPageLoader';
import { DashboardPage } from '@/routes/DashboardPage';
import { LoginPage } from '@/routes/LoginPage';
import { NotFoundPage } from '@/routes/NotFoundPage';
import { RegisterPage } from '@/routes/RegisterPage';

export function App(): JSX.Element {
  return (
    <Routes>
      <Route path="/" element={<Landing />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />

      <Route element={<ProtectedRoute />}>
        <Route path="/dashboard" element={<DashboardPage />} />
      </Route>

      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}

/** Sends the visitor wherever their session says they belong, once it is known. */
function Landing(): JSX.Element {
  const { state } = useAuth();

  if (state.status === 'restoring') {
    return <FullPageLoader label="Loading Placefy" />;
  }

  return <Navigate to={state.status === 'authenticated' ? '/dashboard' : '/login'} replace />;
}
