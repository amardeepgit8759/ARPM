import { useQuery } from '@tanstack/react-query';
import { adminApi } from '@/api/admin';
import { ApiError, messageFor } from '@/api/problem';
import { Alert } from '@/components/Alert';
import { Button } from '@/components/Button';
import { EmptyState } from '@/components/EmptyState';
import { PageHeader } from '@/components/PageHeader';
import { Panel, PanelBody, PanelHeader } from '@/components/Panel';

/**
 * Platform-wide figures for administrators. Aggregates only (ADR-007): nothing on this screen
 * identifies a student, because the API it reads cannot return one.
 */
export function AdminDashboardPage(): JSX.Element {
  const overview = useQuery({
    queryKey: ['admin', 'overview'],
    queryFn: adminApi.overview,
    retry: (failureCount, error) =>
      !(error instanceof ApiError && (error.status === 401 || error.status === 403)) && failureCount < 2,
  });

  return (
    <>
      <PageHeader
        title="Admin dashboard"
        description="Platform-wide totals. Individual students' data is never shown here."
      />

      <Panel>
        <PanelHeader title="Students" description="Accounts with the student role. Administrators are not counted." />
        <PanelBody>
          {overview.isPending ? (
            <div role="status" aria-live="polite">
              <span className="sr-only">Loading platform totals</span>
              <div className="bg-border h-9 w-24 animate-pulse rounded" />
            </div>
          ) : null}

          {overview.isError ? (
            <div className="flex flex-col items-start gap-3">
              <Alert title="Could not load platform totals">{messageFor(overview.error)}</Alert>
              <Button variant="secondary" onClick={() => void overview.refetch()}>
                Try again
              </Button>
            </div>
          ) : null}

          {overview.isSuccess && overview.data.studentCount === 0 ? (
            <EmptyState
              title="No students yet"
              description="Nobody has registered as a student. Totals appear here as soon as someone does."
            />
          ) : null}

          {overview.isSuccess && overview.data.studentCount > 0 ? (
            <p className="flex items-baseline gap-2">
              <span className="text-ink text-3xl font-semibold">{overview.data.studentCount}</span>
              <span className="text-ink-muted text-sm">
                registered {overview.data.studentCount === 1 ? 'student' : 'students'}
              </span>
            </p>
          ) : null}
        </PanelBody>
      </Panel>
    </>
  );
}
