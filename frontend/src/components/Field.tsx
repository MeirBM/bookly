"use client";

import type { ReactNode } from "react";

/** A labelled input whose error is announced, not only coloured. */
export function Field({
  label,
  error,
  children,
}: {
  label: string;
  error?: string;
  children: ReactNode;
}) {
  return (
    <label className="block">
      <span className="mb-1 block text-sm font-medium text-slate-700">{label}</span>
      {children}
      {error ? (
        <span role="alert" className="mt-1 block text-sm text-red-700">
          {error}
        </span>
      ) : null}
    </label>
  );
}

export const inputClass =
  "w-full rounded-md border border-slate-300 px-3 py-2 text-slate-900 " +
  "focus:border-slate-900 focus:outline-none focus:ring-1 focus:ring-slate-900";

export const buttonClass =
  "w-full rounded-md bg-slate-900 px-4 py-2 font-medium text-white " +
  "hover:bg-slate-700 disabled:cursor-not-allowed disabled:bg-slate-400";
