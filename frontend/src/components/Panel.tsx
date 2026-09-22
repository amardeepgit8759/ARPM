import type { ReactNode } from 'react';

/** A bordered surface. 1px border, no shadow — separation without decoration. */
export function Panel({ children }: { children: ReactNode }): JSX.Element {
  return <div className="border-border bg-surface rounded border">{children}</div>;
}

export function PanelHeader({ title, description }: { title: string; description?: string }) {
  return (
    <div className="border-border border-b px-4 py-3">
      <h2 className="text-ink text-sm font-semibold">{title}</h2>
      {description ? <p className="text-ink-subtle mt-0.5 text-xs">{description}</p> : null}
    </div>
  );
}

export function PanelBody({ children }: { children: ReactNode }): JSX.Element {
  return <div className="px-4 py-4">{children}</div>;
}
