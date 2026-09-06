import type { ReactNode } from "react";

type Tone = "neutral" | "brand" | "success" | "danger";

const TONES: Record<Tone, string> = {
  neutral: "bg-surface-muted text-ink-muted border-border",
  brand: "bg-brand-50 text-brand-700 border-brand-200",
  success: "bg-success-50 text-success-700 border-success-200",
  danger: "bg-danger-50 text-danger-700 border-danger-200",
};

/**
 * A small status label.
 *
 * <p>It carries its own text rather than relying on colour, because the standard forbids
 * communicating through colour alone — someone who cannot distinguish the tones still reads
 * "cancelled".
 */
export function Badge({ tone = "neutral", children }: { tone?: Tone; children: ReactNode }) {
  return (
    <span
      className={`inline-flex items-center rounded-full border px-2 py-0.5 text-xs font-medium ${TONES[tone]}`}
    >
      {children}
    </span>
  );
}
