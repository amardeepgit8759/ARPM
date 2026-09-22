import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { z } from 'zod';
import { ApiError, PROBLEM_TYPES, type Problem } from '@/api/problem';
import { useAuth } from '@/auth/useAuth';
import { Alert } from '@/components/Alert';
import { Button } from '@/components/Button';
import { Field } from '@/components/Field';
import { AuthLayout } from './AuthLayout';

/**
 * Mirrors the server's rules so the user finds out before a round trip. The server is still the
 * authority — these are a courtesy, not a substitute, and the API rejects the same inputs.
 */
const registerSchema = z.object({
  name: z.string().trim().min(2, 'Enter your full name.').max(120, 'Name is too long.'),
  email: z.string().trim().min(1, 'Enter your email address.').email('Enter a valid email address.'),
  password: z
    .string()
    .min(12, 'Use at least 12 characters.')
    // BCrypt ignores anything past 72 bytes, so the server rejects longer passwords outright.
    .refine((value) => new TextEncoder().encode(value).length <= 72, 'Password is too long.'),
});

type RegisterValues = z.infer<typeof registerSchema>;

export function RegisterPage(): JSX.Element {
  const { state, register: createAccount } = useAuth();
  const navigate = useNavigate();
  const [problem, setProblem] = useState<Problem | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<RegisterValues>({ resolver: zodResolver(registerSchema) });

  if (state.status === 'authenticated') {
    return <Navigate to="/dashboard" replace />;
  }

  async function onSubmit(values: RegisterValues): Promise<void> {
    setProblem(null);
    try {
      await createAccount(values);
      navigate('/dashboard', { replace: true });
    } catch (error) {
      if (error instanceof ApiError && error.is(PROBLEM_TYPES.emailAlreadyRegistered)) {
        setError('email', { message: 'An account with that email already exists.' });
        return;
      }
      // A field-scoped validation failure lands on that field; anything else is a page-level
      // banner, so a rule the client does not mirror still reaches the user.
      if (error instanceof ApiError && error.problem.field === 'password') {
        setError('password', { message: error.problem.detail ?? 'Password was rejected.' });
        return;
      }
      setProblem(describe(error));
    }
  }

  return (
    <AuthLayout
      title="Create your account"
      subtitle="Set a target company and role, then find out where you stand."
      footer={
        <>
          Already registered?{' '}
          <Link to="/login" className="text-accent hover:text-accent-strong underline">
            Sign in
          </Link>
        </>
      }
    >
      <form onSubmit={handleSubmit(onSubmit)} noValidate className="flex flex-col gap-4">
        {problem ? <Alert title={problem.title}>{problem.detail}</Alert> : null}

        <Field
          label="Full name"
          autoComplete="name"
          autoFocus
          error={errors.name?.message}
          {...register('name')}
        />

        <Field
          label="Email"
          type="email"
          autoComplete="email"
          error={errors.email?.message}
          {...register('email')}
        />

        <Field
          label="Password"
          type="password"
          autoComplete="new-password"
          hint="At least 12 characters."
          error={errors.password?.message}
          {...register('password')}
        />

        <Button type="submit" loading={isSubmitting}>
          {isSubmitting ? 'Creating account' : 'Create account'}
        </Button>
      </form>
    </AuthLayout>
  );
}

function describe(error: unknown): Problem {
  if (error instanceof ApiError) {
    return error.problem;
  }
  return {
    type: 'about:blank',
    title: 'Could not reach Placefy',
    status: 0,
    detail: 'Check your connection and try again.',
  };
}
