interface EmptyStateProps {
  title: string;
  description: string;
}

/**
 * Says what is absent and why, rather than showing a zero.
 *
 * This matters more here than in most products: a zero on an APRM screen is a claim that a
 * scoring run measured something and got zero. "No assessment yet" is a different statement,
 * and the two must never look alike.
 */
export function EmptyState({ title, description }: EmptyStateProps): JSX.Element {
  return (
    <div className="border-border flex flex-col items-start gap-1 rounded border border-dashed px-4 py-6">
      <p className="text-ink text-sm font-medium">{title}</p>
      <p className="text-ink-subtle max-w-prose text-sm">{description}</p>
    </div>
  );
}
