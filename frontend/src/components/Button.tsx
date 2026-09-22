import type { ButtonHTMLAttributes, ReactNode } from 'react';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary';
  loading?: boolean;
  children: ReactNode;
}

const VARIANTS = {
  primary: 'bg-accent text-white hover:bg-accent-strong disabled:bg-border-strong',
  secondary:
    'bg-surface text-ink border border-border-strong hover:bg-canvas disabled:text-ink-subtle',
} as const;

export function Button({
  variant = 'primary',
  loading = false,
  children,
  ...props
}: ButtonProps): JSX.Element {
  return (
    <button
      {...props}
      // Disabling while in flight is the whole double-submit guard. Without it, an impatient
      // second click on Register is a second POST and a 409 on an account you just created.
      disabled={props.disabled === true || loading}
      aria-busy={loading || undefined}
      className={[
        'inline-flex items-center justify-center gap-2 rounded px-3.5 py-2',
        'text-sm font-medium transition-colors disabled:cursor-not-allowed',
        VARIANTS[variant],
        props.className ?? '',
      ].join(' ')}
    >
      {loading ? <Spinner /> : null}
      {children}
    </button>
  );
}

function Spinner(): JSX.Element {
  return (
    <span
      aria-hidden="true"
      className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-current border-t-transparent"
    />
  );
}
