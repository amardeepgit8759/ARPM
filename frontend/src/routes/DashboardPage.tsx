import { useQuery } from '@tanstack/react-query';
import { accountApi } from '@/api/account';
import { ApiError } from '@/api/problem';
import { Alert } from '@/components/Alert';
import { Button } from '@/components/Button';
import { EmptyState } from '@/components/EmptyState';
import { PageHeader } from '@/components/PageHeader';
import { Panel, PanelBody, PanelHeader } from '@/components/Panel';

/**
 * The student's home screen.
 *
 * It renders the profile the API returns and nothing else. There is no readiness score here and
 * there will not be one until a scoring run exists to read it from — every number on an APRM
 * screen has to trace to a persisted, versioned run, and inventing a placeholder number now is
 * exactly the habit that rule exists to prevent.
 */
export function DashboardPage(): JSX.Element {
  const profile = useQuery({
    queryKey: ['me'],
    queryFn: accountApi.me,
    retry: (failureCount, error) =>
      // A 401 will not become a 200 by asking again.
      !(error instanceof ApiError && error.status === 401) && failureCount < 2,
  });

  return (
    <>
      <PageHeader
        title="Dashboard"
        description="Your readiness, gaps and next steps appear here as you complete assessments."
      />

      <Panel>
        <PanelHeader title="Account" description="Read live from your APRM account." />
        <PanelBody>
          {profile.isPending ? <ProfileSkeleton /> : null}

          {profile.isError ? (
            <div className="flex flex-col items-start gap-3">
              <Alert title="Could not load your profile">{describe(profile.error)}</Alert>
              <Button variant="secondary" onClick={() => void profile.refetch()}>
                Try again
              </Button>
            </div>
          ) : null}

          {profile.isSuccess ? (
            <dl className="grid grid-cols-1 gap-x-8 gap-y-3 sm:grid-cols-[8rem_1fr]">
              <Detail label="Name" value={profile.data.name} />
              <Detail label="Email" value={profile.data.email} />
              <Detail label="Role" value={profile.data.role} />
              <Detail
                label="Member since"
                value={new Date(profile.data.createdAt).toISOString().slice(0, 10)}
              />
            </dl>
          ) : null}
        </PanelBody>
      </Panel>

      <Panel>
        <PanelHeader title="Readiness" description="Appears once you complete an assessment." />
        <PanelBody>
          <EmptyState
            title="No assessment yet"
            description="APRM shows a readiness score only after an assessment has been scored and the run stored. Nothing is estimated in the meantime, so this stays empty rather than showing a zero."
          />
        </PanelBody>
      </Panel>
    </>
  );
}

function Detail({ label, value }: { label: string; value: string }): JSX.Element {
  return (
    <>
      <dt className="text-ink-subtle text-sm">{label}</dt>
      <dd className="text-ink text-sm break-words">{value}</dd>
    </>
  );
}

function ProfileSkeleton(): JSX.Element {
  return (
    <div className="flex flex-col gap-3" role="status" aria-live="polite">
      <span className="sr-only">Loading your profile</span>
      {[0, 1, 2, 3].map((row) => (
        <div key={row} className="bg-border h-4 w-64 max-w-full animate-pulse rounded" />
      ))}
    </div>
  );
}

function describe(error: unknown): string {
  if (error instanceof ApiError) {
    return error.problem.detail ?? error.problem.title;
  }
  return 'Check your connection and try again.';
}
