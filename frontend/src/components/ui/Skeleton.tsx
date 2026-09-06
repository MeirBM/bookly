/**
 * A placeholder with the shape of the content that is coming.
 *
 * <p>Preferred over a spinner because it says something a spinner cannot: how much is arriving and
 * roughly what it looks like. Callers pair it with a visible label, so the state is announced as
 * well as drawn — a skeleton alone is silent to a screen reader.
 */
export function Skeleton({ className = "" }: { className?: string }) {
  return (
    <div
      aria-hidden="true"
      className={`animate-pulse rounded-md bg-surface-muted ${className}`}
    />
  );
}

/** Rows shaped like a list, for the screens that load one. */
export function SkeletonRows({ rows = 3 }: { rows?: number }) {
  return (
    <div className="flex flex-col gap-2" aria-hidden="true">
      {Array.from({ length: rows }, (_, index) => (
        <div
          key={index}
          className="flex items-center justify-between rounded-lg border border-border bg-surface px-4 py-3.5"
        >
          <div className="flex w-full flex-col gap-2">
            <Skeleton className="h-4 w-1/3" />
            <Skeleton className="h-3 w-1/2" />
          </div>
        </div>
      ))}
    </div>
  );
}
