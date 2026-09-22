import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { z } from 'zod';
import { ApiError, PROBLEM_TYPES, type Problem } from '@/api/problem';
import { useAuth } from '@/auth/useAuth';
import { Alert } from '@/components/Alert';
import { Button } from '@/components/Button';
import { Field } from '@/components/Field';
import { AuthLayout } from './AuthLayout';

/**
 * Presence only. The server decides whether a password is acceptable, and on login it must not
 * apply today's minimum length to a password set under an older rule — a client-side length
 * check here would lock those users out of their own accounts.
 */
const loginSchema = z.object({
  email: z.string().min(1, 'Enter your email address.'),
  password: z.string().min(1, 'Enter your password.'),
});

type LoginValues = z.infer<typeof loginSchema>;

export function LoginPage(): JSX.Element {
  const { state, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [problem, setProblem] = useState<Problem | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginValues>({ resolver: zodResolver(loginSchema) });

  if (state.status === 'authenticated') {
    return <Navigate to="/dashboard" replace />;
  }

  const destination = (location.state as { from?: string } | null)?.from ?? '/dashboard';

  async function onSubmit(values: LoginValues): Promise<void> {
    setProblem(null);
    try {
      await login(values);
      navigate(destination, { replace: true });
    } catch (error) {
      setProblem(describe(error));
    }
  }

  return (
    <AuthLayout
      title="Sign in"
      subtitle="Continue your placement preparation."
      footer={
        <>
          No account yet?{' '}
          <Link to="/register" className="text-accent hover:text-accent-strong underline">
            Create one
          </Link>
        </>
      }
    >
      <form onSubmit={handleSubmit(onSubmit)} noValidate className="flex flex-col gap-4">
        {problem ? <Alert title={problem.title}>{problem.detail}</Alert> : null}

        <Field
          label="Email"
          type="email"
          autoComplete="email"
          autoFocus
          error={errors.email?.message}
          {...register('email')}
        />

        <Field
          label="Password"
          type="password"
          autoComplete="current-password"
          error={errors.password?.message}
          {...register('password')}
        />

        <Button type="submit" loading={isSubmitting}>
          {isSubmitting ? 'Signing in' : 'Sign in'}
        </Button>
      </form>
    </AuthLayout>
  );
}

function describe(error: unknown): Problem {
  if (error instanceof ApiError) {
    if (error.is(PROBLEM_TYPES.invalidCredentials)) {
      // Repeat the server's deliberately vague wording. Saying "no account with that email"
      // would hand back exactly the account-enumeration signal the backend spends effort hiding.
      return {
        type: error.problem.type,
        title: 'Sign in failed',
        status: error.status,
        detail: 'Email or password is incorrect.',
      };
    }
    return error.problem;
  }

  return {
    type: 'about:blank',
    title: 'Could not reach Placefy',
    status: 0,
    detail: 'Check your connection and try again.',
  };
}
