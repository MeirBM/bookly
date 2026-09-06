import type { ReactNode } from "react";

/**
 * A grouping surface: subtle border, barely-there shadow, comfortable padding.
 *
 * <p>The standard is explicit that not everything belongs in a card, so this exists to be used
 * where grouping genuinely helps rather than as the default wrapper for every block on a page.
 */
export function Card({
  children,
  className = "",
  padded = true,
}: {
  children: ReactNode;
  className?: string;
  padded?: boolean;
}) {
  return (
    <div
      className={[
        "rounded-lg border border-border bg-surface shadow-card",
        padded ? "p-5" : "",
        className,
      ].join(" ")}
    >
      {children}
    </div>
  );
}
