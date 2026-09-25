import { Link } from 'react-router-dom';

export function NotFoundPage(): JSX.Element {
  return (
    <main className="flex min-h-screen items-center justify-center px-4">
      <div className="max-w-sm">
        <h1 className="text-ink text-lg font-semibold">Page not found</h1>
        <p className="text-ink-muted mt-1 text-sm">
          That address does not match anything in APRM.
        </p>
        <Link
          to="/"
          className="text-accent hover:text-accent-strong mt-4 inline-block text-sm underline"
        >
          Back to the start
        </Link>
      </div>
    </main>
  );
}
