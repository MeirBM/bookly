/**
 * Hand-off to whatever calendar the customer already uses.
 *
 * <p>No account, no OAuth, no backend: an `.ics` file is a few lines of text and a Google link is a
 * URL. Both are built from one function, so the two exports cannot drift into disagreeing about
 * when the appointment is — which is the failure a customer would only discover by turning up on
 * the wrong day.
 */
export type CalendarEvent = {
  title: string;
  description: string;
  location: string;
  /** ISO instants. The moment, not a local reading of it. */
  startsAt: string;
  endsAt: string;
};

/**
 * Instants as UTC basic-format timestamps: `20260907T060000Z`.
 *
 * <p>The `Z` is the whole point. Without it a calendar reads the time in whatever zone the reader's
 * machine happens to be in, and a booking made for 09:00 in Jerusalem lands at 09:00 in London.
 */
function asUtcStamp(iso: string): string {
  return new Date(iso).toISOString().replace(/[-:]/g, "").replace(/\.\d{3}/, "");
}

/** RFC 5545 asks for CRLF, and clients that dislike a file reject it without saying so. */
function icsLine(name: string, value: string): string {
  const escaped = value
    .replace(/\\/g, "\\\\")
    .replace(/;/g, "\;")
    .replace(/,/g, "\\,")
    .replace(/\r?\n/g, "\\n");
  return `${name}:${escaped}`;
}

export function toIcsFile(event: CalendarEvent, uid: string): string {
  return [
    "BEGIN:VCALENDAR",
    "VERSION:2.0",
    "PRODID:-//Bookly//Booking//EN",
    "CALSCALE:GREGORIAN",
    "METHOD:PUBLISH",
    "BEGIN:VEVENT",
    icsLine("UID", `${uid}@bookly`),
    icsLine("DTSTAMP", asUtcStamp(new Date().toISOString())),
    icsLine("DTSTART", asUtcStamp(event.startsAt)),
    icsLine("DTEND", asUtcStamp(event.endsAt)),
    icsLine("SUMMARY", event.title),
    icsLine("DESCRIPTION", event.description),
    icsLine("LOCATION", event.location),
    "END:VEVENT",
    "END:VCALENDAR",
  ].join("\r\n");
}

/** The same instants as the file, because they are read from the same event. */
export function toGoogleCalendarUrl(event: CalendarEvent): string {
  const query = new URLSearchParams({
    action: "TEMPLATE",
    text: event.title,
    dates: `${asUtcStamp(event.startsAt)}/${asUtcStamp(event.endsAt)}`,
    details: event.description,
    location: event.location,
  });
  return `https://calendar.google.com/calendar/render?${query.toString()}`;
}

/** A data URL rather than a blob: no object to revoke, and nothing to leak if the page stays open. */
export function toIcsDataUrl(event: CalendarEvent, uid: string): string {
  return `data:text/calendar;charset=utf-8,${encodeURIComponent(toIcsFile(event, uid))}`;
}
