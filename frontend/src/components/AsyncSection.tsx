"use client";

import type { UseQueryResult } from "@tanstack/react-query";
import type { ReactNode } from "react";

/**
 * Renders the four states every data-backed screen owes its reader: loading, error, empty, content.
 *
 * <p>A shared component rather than a convention, because "remember to add an empty state" is the
 * kind of instruction that holds for three screens and then quietly stops. An agent builds the happy
 * path and stops; this makes the other three paths the default and the happy path the special case.
 */
export function AsyncSection<T>({
  query,
  isEmpty,
  empty,
  children,
  label,
}: {
  query: UseQueryResult<T>;
  isEmpty: (data: T) => boolean;
  empty: ReactNode;
  children: (data: T) => ReactNode;
  label: string;
}) {
  if (query.isPending) {
    return (
      <p className="py-6 text-slate-600" role="status">
        Loading {label}…
      </p>
    );
  }

  if (query.isError) {
    return (
      <div className="py-4" role="alert">
        <p className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-800">
          Could not load {label}.
        </p>
        <button className="mt-2 text-sm underline" type="button" onClick={() => query.refetch()}>
          Try again
        </button>
      </div>
    );
  }

  if (isEmpty(query.data)) {
    return (
      <div className="rounded-md border border-dashed border-slate-300 p-6 text-slate-600">
        {empty}
      </div>
    );
  }

  return <>{children(query.data)}</>;
}
