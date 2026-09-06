"use client";

import type { InputHTMLAttributes, SelectHTMLAttributes } from "react";

/**
 * Field chrome, in one place.
 *
 * <p>Focus is not styled here: the global `:focus-visible` rule in globals.css gives every
 * focusable element the same brand ring, so a control added later cannot quietly ship without one.
 */
const FIELD =
  "w-full rounded-md border border-border-strong bg-surface px-3 text-sm text-ink " +
  "placeholder:text-ink-faint transition-colors duration-150 " +
  "hover:border-ink-faint disabled:cursor-not-allowed disabled:bg-surface-muted " +
  "disabled:text-ink-subtle aria-[invalid=true]:border-danger-600";

export function Input({ className = "", ...rest }: InputHTMLAttributes<HTMLInputElement>) {
  return <input className={`${FIELD} h-11 ${className}`} {...rest} />;
}

export function Select({ className = "", ...rest }: SelectHTMLAttributes<HTMLSelectElement>) {
  return <select className={`${FIELD} h-11 pr-8 ${className}`} {...rest} />;
}
