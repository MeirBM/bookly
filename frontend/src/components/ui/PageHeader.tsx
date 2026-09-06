import type { ReactNode } from "react";

/**
 * Title, one line of context, and the primary action.
 *
 * <p>The standard's dashboard hierarchy starts here: header, then the primary action, then the
 * content. Putting the action in the header keeps it in the same place on every screen instead of
 * wherever each page happened to leave it.
 */
export function PageHeader({
  title,
  description,
  action,
}: {
  title: string;
  description?: ReactNode;
  action?: ReactNode;
}) {
  return (
    <header className="flex flex-wrap items-start justify-between gap-4">
      <div className="min-w-0">
        <h1 className="text-2xl font-semibold tracking-tight text-ink sm:text-3xl">{title}</h1>
        {description ? (
          <p className="mt-1.5 max-w-2xl text-sm text-ink-muted">{description}</p>
        ) : null}
      </div>
      {action ? <div className="shrink-0">{action}</div> : null}
    </header>
  );
}
