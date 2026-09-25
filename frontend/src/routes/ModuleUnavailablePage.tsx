import { EmptyState } from '@/components/EmptyState';
import { PageHeader } from '@/components/PageHeader';

/**
 * The page behind a menu entry whose module is not built yet. It says what is coming and what it
 * waits on. It deliberately shows no preview or sample figures: a sample number on screen would
 * look exactly like a real one.
 */
export function ModuleUnavailablePage({ title, description }: { title: string; description: string }): JSX.Element {
  return (
    <>
      <PageHeader title={title} />
      <EmptyState title="Not available yet" description={description} />
    </>
  );
}
