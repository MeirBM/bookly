import type { ReactNode } from "react";

/**
 * What a screen says when it has nothing to show.
 *
 * <p>The standard asks an empty state to answer three things — what is missing, why it matters,
 * and what to do next — and this shape makes the third one hard to forget: an empty state with no
 * action is usually a screen that has left the reader stuck.
 */
export function EmptyState({
  title,
  description,
  action,
}: {
  title: string;
  description: ReactNode;
  action?: ReactNode;
}) {
  return (
    <div className="rounded-lg border border-dashed border-border-strong bg-surface px-6 py-10 text-center">
      <p className="text-base font-medium text-ink">{title}</p>
      <p className="mx-auto mt-1.5 max-w-md text-sm text-ink-muted">{description}</p>
      {action ? <div className="mt-5 flex justify-center">{action}</div> : null}
    </div>
  );
}
