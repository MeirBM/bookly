"use client";

import type { ReactNode } from "react";

/**
 * A labelled control with its error message.
 *
 * <p>The error is announced as well as coloured. The standard forbids communicating through colour
 * alone, and a red border tells a screen reader nothing.
 */
export function Field({
  label,
  error,
  hint,
  children,
}: {
  label: string;
  error?: string;
  hint?: string;
  children: ReactNode;
}) {
  return (
    <label className="block">
      <span className="mb-1.5 block text-sm font-medium text-ink">{label}</span>
      {children}
      {hint && !error ? <span className="mt-1.5 block text-xs text-ink-subtle">{hint}</span> : null}
      {error ? (
        <span role="alert" className="mt-1.5 block text-sm text-danger-700">
          {error}
        </span>
      ) : null}
    </label>
  );
}

/**
 * Kept so the screens that have not been rebuilt yet pick up the new surface, spacing and focus
 * treatment without being edited. New code should use `<Input>` and `<Button>` from `ui/`; these
 * are a migration path, not a second way of doing it.
 */
export const inputClass =
  "w-full h-11 rounded-md border border-border-strong bg-surface px-3 text-sm text-ink " +
  "placeholder:text-ink-faint transition-colors duration-150 hover:border-ink-faint " +
  "disabled:cursor-not-allowed disabled:bg-surface-muted";

export const buttonClass =
  "inline-flex h-11 w-full items-center justify-center gap-2 rounded-md bg-brand-gradient " +
  "px-5 text-sm font-medium text-white shadow-card transition-[box-shadow,filter] duration-150 " +
  "hover:shadow-card-hover hover:brightness-105 active:brightness-95 " +
  "disabled:cursor-not-allowed disabled:opacity-55 disabled:brightness-100";
