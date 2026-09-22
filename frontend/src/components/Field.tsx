import { forwardRef, useId, type InputHTMLAttributes } from 'react';

interface FieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  error?: string | undefined;
  hint?: string | undefined;
}

/**
 * A labelled input that wires up its own describedby and aria-invalid. Doing this by hand at
 * each call site is how a form ends up with error text that a screen reader never announces.
 */
export const Field = forwardRef<HTMLInputElement, FieldProps>(function Field(
  { label, error, hint, ...inputProps },
  ref,
) {
  const id = useId();
  const errorId = `${id}-error`;
  const hintId = `${id}-hint`;
  const describedBy = [error ? errorId : null, hint ? hintId : null].filter(Boolean).join(' ');

  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-ink text-sm font-medium">
        {label}
      </label>

      <input
        {...inputProps}
        id={id}
        ref={ref}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy.length > 0 ? describedBy : undefined}
        className={[
          'border bg-surface text-ink placeholder:text-ink-subtle rounded px-3 py-2 text-sm',
          'focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent',
          'disabled:bg-canvas disabled:text-ink-subtle',
          error ? 'border-danger-border' : 'border-border-strong',
        ].join(' ')}
      />

      {hint ? (
        <p id={hintId} className="text-ink-subtle text-xs">
          {hint}
        </p>
      ) : null}

      {error ? (
        <p id={errorId} className="text-danger text-xs">
          {error}
        </p>
      ) : null}
    </div>
  );
});
