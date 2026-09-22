import type { ReactNode } from 'react';

interface AlertProps {
  title: string;
  children?: ReactNode;
}

/**
 * `role="alert"` so a failure is announced rather than only drawn. A submitted form that fails
 * silently for a screen-reader user is a broken form.
 */
export function Alert({ title, children }: AlertProps): JSX.Element {
  return (
    <div
      role="alert"
      className="border-danger-border bg-danger-subtle flex flex-col gap-1 rounded border px-3 py-2.5"
    >
      <p className="text-danger text-sm font-medium">{title}</p>
      {children ? <div className="text-ink-muted text-sm">{children}</div> : null}
    </div>
  );
}
