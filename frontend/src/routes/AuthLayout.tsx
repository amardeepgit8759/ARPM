import type { ReactNode } from 'react';

interface AuthLayoutProps {
  title: string;
  subtitle: string;
  children: ReactNode;
  footer: ReactNode;
}

export function AuthLayout({ title, subtitle, children, footer }: AuthLayoutProps): JSX.Element {
  return (
    <main className="flex min-h-screen items-center justify-center px-4 py-12">
      <div className="w-full max-w-sm">
        <div className="mb-6">
          <p className="text-ink text-base font-semibold tracking-tight">APRM</p>
          <h1 className="text-ink mt-4 text-lg font-semibold">{title}</h1>
          <p className="text-ink-muted mt-1 text-sm">{subtitle}</p>
        </div>

        <div className="border-border bg-surface rounded border px-5 py-5">{children}</div>

        <p className="text-ink-subtle mt-4 text-sm">{footer}</p>
      </div>
    </main>
  );
}
