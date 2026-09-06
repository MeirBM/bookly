"use client";

import type { AvailableSlot } from "@/lib/api";

/**
 * The times a visitor can actually take.
 *
 * <p>Each button's text is the time and nothing else. That is a deliberate constraint rather than
 * a stylistic one: the accessible name is what a screen reader announces and what the browser
 * tests select by, so duration or staff names belong around the grid, not inside the control.
 *
 * <p>Selection is obvious by fill, weight and a mark — never by colour alone, and `aria-pressed`
 * carries it for anyone not looking.
 */
export function SlotGrid({
  slots,
  selected,
  format,
  onSelect,
}: {
  slots: AvailableSlot[];
  selected: string | null;
  format: (instant: string) => string;
  onSelect: (start: string) => void;
}) {
  return (
    <ul
      data-testid="slots"
      className="grid grid-cols-3 gap-2 sm:grid-cols-4 md:grid-cols-6"
    >
      {slots.map((slot) => {
        const isSelected = selected === slot.start;
        return (
          <li key={slot.start}>
            <button
              type="button"
              aria-pressed={isSelected}
              onClick={() => onSelect(slot.start)}
              className={[
                // 44px minimum: a time is the one thing on this page a thumb must hit first time.
                "flex h-11 w-full items-center justify-center rounded-md border text-sm",
                "font-medium tabular-nums transition-all duration-150",
                isSelected
                  ? "border-transparent bg-brand-gradient text-white shadow-card"
                  : "border-border-strong bg-surface text-ink hover:-translate-y-px hover:border-brand-400 hover:text-brand-700 hover:shadow-card",
              ].join(" ")}
            >
              {format(slot.start)}
            </button>
          </li>
        );
      })}
    </ul>
  );
}
