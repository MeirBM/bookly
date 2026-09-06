/**
 * Where the visitor is in the journey.
 *
 * <p>The standard asks that the path be obvious: business, service, person, date, time, details,
 * confirmation. Showing it costs one row and removes the question "how much more of this is
 * there?", which is what makes a booking form feel long even when it is short.
 *
 * <p>State is carried by text and a mark, not by colour alone.
 */
const STEPS = ["Service", "Person", "Date", "Time", "Details"] as const;

export function BookingSteps({ current }: { current: (typeof STEPS)[number] }) {
  const index = STEPS.indexOf(current);

  return (
    <ol className="flex flex-wrap items-center gap-x-2 gap-y-1 text-xs" aria-label="Booking steps">
      {STEPS.map((step, position) => {
        const done = position < index;
        const active = position === index;
        return (
          <li key={step} className="flex items-center gap-2">
            <span
              aria-current={active ? "step" : undefined}
              className={[
                "rounded-full px-2.5 py-1 font-medium transition-colors duration-150",
                active ? "bg-brand-600 text-white" : "",
                done ? "bg-brand-50 text-brand-700" : "",
                !active && !done ? "text-ink-faint" : "",
              ].join(" ")}
            >
              {done ? "Done · " : ""}
              {step}
            </span>
            {position < STEPS.length - 1 ? (
              <span aria-hidden="true" className="text-ink-faint">
                ›
              </span>
            ) : null}
          </li>
        );
      })}
    </ol>
  );
}
