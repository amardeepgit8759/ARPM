import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { accountApi } from '@/api/account';
import { ApiError, PROBLEM_TYPES, messageFor } from '@/api/problem';
import type { User } from '@/api/schemas';
import { useAuth } from '@/auth/useAuth';
import { Alert } from '@/components/Alert';
import { Button } from '@/components/Button';
import { Field } from '@/components/Field';
import { PageHeader } from '@/components/PageHeader';
import { Panel, PanelBody, PanelHeader } from '@/components/Panel';
import { saveFile } from '@/lib/saveFile';

/**
 * The caller's own account: details, password, their data, and deletion.
 *
 * Every rule that matters is enforced by the API. The form schemas below repeat the simple ones
 * only so a typo is caught before a round trip.
 */
export function ProfilePage(): JSX.Element {
  const profile = useQuery({
    queryKey: ['me'],
    queryFn: accountApi.me,
    retry: (failureCount, error) => !(error instanceof ApiError && error.status === 401) && failureCount < 2,
  });

  return (
    <>
      <PageHeader title="Profile" description="Your account details, password and data." />

      {profile.isPending ? <ProfileSkeleton /> : null}

      {profile.isError ? (
        <div className="flex flex-col items-start gap-3">
          <Alert title="Could not load your profile">{messageFor(profile.error)}</Alert>
          <Button variant="secondary" onClick={() => void profile.refetch()}>
            Try again
          </Button>
        </div>
      ) : null}

      {profile.isSuccess ? (
        <>
          <DetailsPanel user={profile.data} />
          <PasswordPanel />
          <DataPanel />
          <DeletePanel />
        </>
      ) : null}
    </>
  );
}

// ------------------------------------------------------------------ details

const detailsSchema = z.object({
  name: z.string().trim().min(2, 'Enter your full name.').max(120, 'Name is too long.'),
});

type DetailsValues = z.infer<typeof detailsSchema>;

function DetailsPanel({ user }: { user: User }): JSX.Element {
  const queryClient = useQueryClient();
  const { updateUser } = useAuth();
  const [saved, setSaved] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isDirty },
  } = useForm<DetailsValues>({ resolver: zodResolver(detailsSchema), defaultValues: { name: user.name } });

  const save = useMutation({
    mutationFn: accountApi.updateProfile,
    onSuccess: (updated) => {
      queryClient.setQueryData(['me'], updated);
      updateUser(updated);
      reset({ name: updated.name });
      setSaved(true);
    },
  });

  return (
    <Panel>
      <PanelHeader title="Details" description="Your name appears on your dashboard. Your email is your sign-in and cannot be changed here." />
      <PanelBody>
        <form
          noValidate
          className="flex max-w-md flex-col gap-4"
          onSubmit={handleSubmit((values) => {
            setSaved(false);
            save.mutate({ name: values.name });
          })}
        >
          {save.isError ? <Alert title="Could not save your details">{messageFor(save.error)}</Alert> : null}
          {saved && !isDirty ? (
            <p role="status" className="text-ink-muted text-sm">
              Your details were saved.
            </p>
          ) : null}

          <Field label="Name" autoComplete="name" error={errors.name?.message} {...register('name')} />
          <Field label="Email" value={user.email} readOnly disabled />

          <dl className="text-ink-subtle grid grid-cols-[8rem_1fr] gap-y-1 text-sm">
            <dt>Role</dt>
            <dd className="text-ink">{user.role === 'ADMIN' ? 'Administrator' : 'Student'}</dd>
            <dt>Member since</dt>
            <dd className="text-ink">{new Date(user.createdAt).toISOString().slice(0, 10)}</dd>
          </dl>

          <div>
            <Button type="submit" loading={save.isPending} disabled={!isDirty}>
              {save.isPending ? 'Saving' : 'Save details'}
            </Button>
          </div>
        </form>
      </PanelBody>
    </Panel>
  );
}

// ------------------------------------------------------------------ password

const passwordSchema = z
  .object({
    // Presence only: a password set under an older policy must still be accepted as "current".
    currentPassword: z.string().min(1, 'Enter your current password.'),
    newPassword: z
      .string()
      .min(12, 'Use at least 12 characters.')
      .refine((value) => new TextEncoder().encode(value).length <= 72, 'Password is too long.'),
    confirmPassword: z.string().min(1, 'Repeat the new password.'),
  })
  .refine((values) => values.newPassword === values.confirmPassword, {
    path: ['confirmPassword'],
    message: 'The two new passwords do not match.',
  });

type PasswordValues = z.infer<typeof passwordSchema>;

function PasswordPanel(): JSX.Element {
  const { adoptSession } = useAuth();
  const [changed, setChanged] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors },
  } = useForm<PasswordValues>({ resolver: zodResolver(passwordSchema) });

  const change = useMutation({
    mutationFn: accountApi.changePassword,
    onSuccess: (session) => {
      adoptSession(session);
      reset();
      setChanged(true);
    },
    onError: (error) => {
      if (error instanceof ApiError && error.is(PROBLEM_TYPES.invalidCredentials)) {
        setError('currentPassword', { message: 'That is not your current password.' });
      } else if (error instanceof ApiError && error.problem.field === 'password') {
        setError('newPassword', { message: error.problem.detail ?? 'Choose a different password.' });
      }
    },
  });

  const unexpectedError =
    change.isError &&
    !(change.error instanceof ApiError &&
      (change.error.is(PROBLEM_TYPES.invalidCredentials) || change.error.problem.field === 'password'));

  return (
    <Panel>
      <PanelHeader
        title="Password"
        description="Changing your password signs you out on every other device."
      />
      <PanelBody>
        <form
          noValidate
          className="flex max-w-md flex-col gap-4"
          onSubmit={handleSubmit((values) => {
            setChanged(false);
            change.mutate({ currentPassword: values.currentPassword, newPassword: values.newPassword });
          })}
        >
          {unexpectedError ? (
            <Alert title="Could not change your password">{messageFor(change.error)}</Alert>
          ) : null}
          {changed ? (
            <p role="status" className="text-ink-muted text-sm">
              Your password was changed. Other devices have been signed out.
            </p>
          ) : null}

          <Field
            label="Current password"
            type="password"
            autoComplete="current-password"
            error={errors.currentPassword?.message}
            {...register('currentPassword')}
          />
          <Field
            label="New password"
            type="password"
            autoComplete="new-password"
            hint="At least 12 characters."
            error={errors.newPassword?.message}
            {...register('newPassword')}
          />
          <Field
            label="Repeat new password"
            type="password"
            autoComplete="new-password"
            error={errors.confirmPassword?.message}
            {...register('confirmPassword')}
          />

          <div>
            <Button type="submit" loading={change.isPending}>
              {change.isPending ? 'Changing password' : 'Change password'}
            </Button>
          </div>
        </form>
      </PanelBody>
    </Panel>
  );
}

// ------------------------------------------------------------------ data

function DataPanel(): JSX.Element {
  const exportData = useMutation({
    mutationFn: accountApi.exportData,
    onSuccess: (blob) => saveFile(blob, 'aprm-export.json'),
  });

  return (
    <Panel>
      <PanelHeader title="Your data" description="Download everything APRM holds about you, as a JSON file." />
      <PanelBody>
        <div className="flex flex-col items-start gap-3">
          {exportData.isError ? (
            <Alert title="Could not prepare your download">{messageFor(exportData.error)}</Alert>
          ) : null}
          {exportData.isSuccess ? (
            <p role="status" className="text-ink-muted text-sm">
              Your download has started.
            </p>
          ) : null}
          <Button variant="secondary" loading={exportData.isPending} onClick={() => exportData.mutate()}>
            {exportData.isPending ? 'Preparing download' : 'Download my data'}
          </Button>
        </div>
      </PanelBody>
    </Panel>
  );
}

// ------------------------------------------------------------------ deletion

const deleteSchema = z.object({ password: z.string().min(1, 'Enter your password to confirm.') });

type DeleteValues = z.infer<typeof deleteSchema>;

function DeletePanel(): JSX.Element {
  const { endSession } = useAuth();
  const [confirming, setConfirming] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    setError,
    setFocus,
    formState: { errors },
  } = useForm<DeleteValues>({ resolver: zodResolver(deleteSchema) });

  const remove = useMutation({
    mutationFn: accountApi.deleteAccount,
    // The account is gone; the protected route sends the now-anonymous visitor to sign in.
    onSuccess: endSession,
    onError: (error) => {
      if (error instanceof ApiError && error.is(PROBLEM_TYPES.invalidCredentials)) {
        setError('password', { message: 'That password is not correct.' });
      }
    },
  });

  useEffect(() => {
    if (confirming) {
      setFocus('password');
    }
  }, [confirming, setFocus]);

  const unexpectedError =
    remove.isError && !(remove.error instanceof ApiError && remove.error.is(PROBLEM_TYPES.invalidCredentials));

  return (
    <Panel>
      <PanelHeader
        title="Delete account"
        description="Permanently erases your account and everything in it. This cannot be undone."
      />
      <PanelBody>
        {!confirming ? (
          <Button variant="secondary" onClick={() => setConfirming(true)}>
            Delete my account
          </Button>
        ) : (
          <form
            noValidate
            className="flex max-w-md flex-col gap-4"
            onSubmit={handleSubmit((values) => remove.mutate(values.password))}
          >
            {unexpectedError ? <Alert title="Could not delete your account">{messageFor(remove.error)}</Alert> : null}
            <Field
              label="Confirm with your password"
              type="password"
              autoComplete="current-password"
              error={errors.password?.message}
              {...register('password')}
            />
            <div className="flex flex-wrap gap-2">
              <Button type="submit" variant="danger" loading={remove.isPending}>
                {remove.isPending ? 'Deleting' : 'Delete permanently'}
              </Button>
              <Button
                type="button"
                variant="secondary"
                disabled={remove.isPending}
                onClick={() => {
                  reset();
                  remove.reset();
                  setConfirming(false);
                }}
              >
                Cancel
              </Button>
            </div>
          </form>
        )}
      </PanelBody>
    </Panel>
  );
}

function ProfileSkeleton(): JSX.Element {
  return (
    <div className="flex flex-col gap-3" role="status" aria-live="polite">
      <span className="sr-only">Loading your profile</span>
      {[0, 1, 2].map((row) => (
        <div key={row} className="bg-border h-24 w-full animate-pulse rounded" />
      ))}
    </div>
  );
}
