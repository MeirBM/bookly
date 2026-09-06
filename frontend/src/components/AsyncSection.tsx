"use client";

import type { UseQueryResult } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { Button } from "@/components/ui/Button";
import { SkeletonRows } from "@/components/ui/Skeleton";

/**
 * Renders the four states every data-backed screen owes its reader: loading, error, empty, content.
 *
 * <p>A shared component rather than a convention, because "remember to add an empty state" is the
 * kind of instruction that holds for three screens and then quietly stops. An agent builds the
 * happy path and stops; this makes the other three the default and the happy path the special case.
 *
 * <p>The loading state is a skeleton *and* a label. The skeleton says how much is coming and
 * roughly what shape it is; the label says it out loud, because a skeleton on its own is silent to
 * anyone not looking at it.
 */
export function AsyncSection<T>({
  query,
  isEmpty,
  empty,
  children,
  label,
  skeletonRows = 3,
}: {
  query: UseQueryResult<T>;
  isEmpty: (data: T) => boolean;
  empty: ReactNode;
  children: (data: T) => ReactNode;
  label: string;
  skeletonRows?: number;
}) {
  if (query.isPending) {
    return (
      <div role="status" aria-live="polite" className="flex flex-col gap-3">
        <p className="text-sm text-ink-subtle">Loading {label}…</p>
        <SkeletonRows rows={skeletonRows} />
      </div>
    );
  }

  if (query.isError) {
    return (
      <div role="alert" className="flex flex-col items-start gap-3">
        <p className="rounded-md border border-danger-200 bg-danger-50 px-3.5 py-2.5 text-sm text-danger-700">
          Could not load {label}.
        </p>
        <Button variant="secondary" size="sm" type="button" onClick={() => query.refetch()}>
          Try again
        </Button>
      </div>
    );
  }

  if (isEmpty(query.data)) {
    return (
      <div className="rounded-lg border border-dashed border-border-strong bg-surface px-6 py-8 text-ink-muted">
        {empty}
      </div>
    );
  }

  return <>{children(query.data)}</>;
}
