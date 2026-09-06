"use client";

import {
  toGoogleCalendarUrl,
  toIcsDataUrl,
  type CalendarEvent,
} from "@/lib/calendar-links";

/**
 * Two links, no accounts.
 *
 * <p>Offered on the confirmation because a booking a customer has to copy into their own calendar
 * is a booking they may still miss — which is the same appointment the business loses, from the
 * other side.
 */
export function AddToCalendar({ event, uid }: { event: CalendarEvent; uid: string }) {
  const link =
    "inline-flex h-10 items-center justify-center rounded-md border border-border-strong " +
    "bg-surface px-4 text-sm font-medium text-ink transition-colors duration-150 " +
    "hover:bg-surface-muted";

  return (
    <div className="flex flex-col gap-2">
      <p className="text-xs font-medium uppercase tracking-wide text-ink-subtle">
        Add to your calendar
      </p>
      <div className="flex flex-wrap gap-2">
        <a className={link} href={toGoogleCalendarUrl(event)} target="_blank" rel="noreferrer">
          Google Calendar
        </a>
        <a className={link} href={toIcsDataUrl(event, uid)} download="bookly-appointment.ics">
          Apple or Outlook
        </a>
      </div>
    </div>
  );
}
