"use client";

import type { ButtonHTMLAttributes, ReactNode } from "react";

type Variant = "primary" | "secondary" | "ghost" | "danger";
type Size = "sm" | "md";

/**
 * The one button in the product.
 *
 * <p>Every state the design standard requires is defined here rather than per screen: hover,
 * active, disabled, loading, and a focus ring inherited from the global rule. A variant that does
 * not exist here should be added here — five near-identical buttons is the failure this replaces.
 */
const VARIANTS: Record<Variant, string> = {
  // The gradient is reserved for the primary action, which is the 20% the palette is for.
  primary:
    "bg-brand-gradient text-white shadow-card hover:shadow-card-hover hover:brightness-105 " +
    "active:brightness-95 disabled:brightness-100",
  secondary:
    "bg-surface text-ink border border-border-strong hover:bg-surface-muted active:bg-border/60",
  ghost: "text-ink-muted hover:text-ink hover:bg-surface-muted active:bg-border/60",
  danger:
    "bg-surface text-danger-700 border border-danger-200 hover:bg-danger-50 active:bg-danger-50",
};

const SIZES: Record<Size, string> = {
  sm: "h-9 px-3 text-sm gap-1.5",
  md: "h-11 px-5 text-sm gap-2",
};

export function Button({
  variant = "primary",
  size = "md",
  loading = false,
  fullWidth = false,
  children,
  className = "",
  disabled,
  ...rest
}: {
  variant?: Variant;
  size?: Size;
  loading?: boolean;
  fullWidth?: boolean;
  children: ReactNode;
} & ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button
      // A loading button stays disabled: the standard's rule that nothing may look clickable and
      // do nothing applies just as much to a second click as to a first.
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={[
        "inline-flex items-center justify-center rounded-md font-medium",
        "transition-[box-shadow,background-color,filter,color] duration-150 ease-out",
        "disabled:cursor-not-allowed disabled:opacity-55",
        VARIANTS[variant],
        SIZES[size],
        fullWidth ? "w-full" : "",
        className,
      ].join(" ")}
      {...rest}
    >
      {loading ? <Spinner /> : null}
      {children}
    </button>
  );
}

/** Small, and only inside a button — the standard rules out full-page spinners, not this. */
function Spinner() {
  return (
    <svg
      className="h-4 w-4 animate-spin"
      viewBox="0 0 16 16"
      fill="none"
      aria-hidden="true"
      focusable="false"
    >
      <circle cx="8" cy="8" r="6.5" stroke="currentColor" strokeOpacity="0.25" strokeWidth="2" />
      <path d="M14.5 8A6.5 6.5 0 0 0 8 1.5" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
    </svg>
  );
}
