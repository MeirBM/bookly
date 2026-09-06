import type { BookingConfirmation as Confirmation } from "@/lib/api";

/**
 * The screen that has to feel like a promise kept.
 *
 * <p>It leads with the fact the visitor came for — the time — and shows it on the *business's*
 * clock, because that is the clock they will turn up on. A reference is included so someone
 * telephoning has something to quote.
 */
export function BookingConfirmation({ confirmation }: { confirmation: Confirmation }) {
  const when = new Intl.DateTimeFormat("en-GB", {
    dateStyle: "full",
    timeStyle: "short",
    timeZone: confirmation.timezone,
  }).format(new Date(confirmation.startsAt));

  return (
    <div className="overflow-hidden rounded-xl border border-border bg-surface shadow-card">
      <div className="bg-brand-gradient px-6 py-5 text-white">
        <p className="text-sm/6 opacity-90">{confirmation.businessName}</p>
        <h1 className="mt-0.5 text-2xl font-semibold tracking-tight">You are booked</h1>
      </div>

      <dl className="flex flex-col gap-4 px-6 py-6">
        <div>
          <dt className="text-xs font-medium uppercase tracking-wide text-ink-subtle">When</dt>
          <dd data-testid="confirmed-when" className="mt-1 text-lg font-medium text-ink">
            {when}{" "}
            <span className="text-sm font-normal text-ink-muted">({confirmation.timezone})</span>
          </dd>
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <div>
            <dt className="text-xs font-medium uppercase tracking-wide text-ink-subtle">Service</dt>
            <dd className="mt-1 text-ink">{confirmation.serviceName}</dd>
          </div>
          <div>
            <dt className="text-xs font-medium uppercase tracking-wide text-ink-subtle">With</dt>
            <dd className="mt-1 text-ink">{confirmation.employeeName}</dd>
          </div>
        </div>
        <div>
          <dt className="text-xs font-medium uppercase tracking-wide text-ink-subtle">Reference</dt>
          <dd className="mt-1 font-mono text-sm text-ink-muted">
            {confirmation.id.slice(0, 8)}
          </dd>
        </div>
      </dl>
    </div>
  );
}
