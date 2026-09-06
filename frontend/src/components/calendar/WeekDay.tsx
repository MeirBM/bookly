import type { Appointment } from "@/lib/api";

/**
 * One column of the week.
 *
 * <p>The heading comes first in the markup and the day's appointments follow it. That order is the
 * screen's meaning — everything under this heading happens on this day — and it is also what makes
 * the calendar readable to anything that flattens the page, from a screen reader to a test.
 */
export function WeekDay({
  label,
  isToday,
  appointments,
  timeOf,
}: {
  label: string;
  isToday: boolean;
  appointments: Appointment[];
  timeOf: (instant: string) => string;
}) {
  return (
    <section
      className={[
        "flex min-h-32 flex-col rounded-lg border bg-surface p-2.5",
        isToday ? "border-brand-300 ring-1 ring-brand-200" : "border-border",
      ].join(" ")}
    >
      <h2
        className={[
          "text-xs font-semibold",
          isToday ? "text-brand-700" : "text-ink-subtle",
        ].join(" ")}
      >
        {label}
        {isToday ? <span className="ml-1 font-normal">· today</span> : null}
      </h2>

      {appointments.length === 0 ? (
        // A quiet dash rather than nothing: an empty day should look deliberate, not unloaded.
        <p aria-hidden="true" className="mt-3 text-sm text-ink-faint">
          —
        </p>
      ) : (
        <ul className="mt-2 flex flex-col gap-1.5">
          {appointments.map((appointment) => (
            <li
              key={appointment.id}
              className="rounded-md border-l-2 border-brand-500 bg-brand-50 px-2 py-1.5 transition-colors duration-150 hover:bg-brand-100"
            >
              <p className="text-xs font-semibold tabular-nums text-brand-800">
                {timeOf(appointment.startsAt)}
              </p>
              <p className="mt-0.5 truncate text-xs font-medium text-ink">
                {appointment.serviceName}
              </p>
              <p className="truncate text-xs text-ink-muted">{appointment.customerName}</p>
              <p className="truncate text-[11px] text-ink-subtle">{appointment.employeeName}</p>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
