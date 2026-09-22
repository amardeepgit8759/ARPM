export function FullPageLoader({ label }: { label: string }): JSX.Element {
  return (
    <div className="flex min-h-screen items-center justify-center" role="status" aria-live="polite">
      <div className="flex items-center gap-2.5">
        <span
          aria-hidden="true"
          className="border-border-strong border-t-accent h-4 w-4 animate-spin rounded-full border-2"
        />
        <span className="text-ink-muted text-sm">{label}</span>
      </div>
    </div>
  );
}
