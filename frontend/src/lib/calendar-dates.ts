/**
 * Calendar dates, as dates rather than as instants.
 *
 * <p>A calendar date is not a point in time, and deriving one with `toISOString()` is the bug this
 * module exists to make unrepeatable: that converts to **UTC**, which is neither the viewer's zone
 * nor the business's. It is right on a UTC machine and wrong for everyone east of it, which is
 * exactly the combination that survives a test suite pinned to UTC and then fails in Jerusalem.
 *
 * <p>A plain date here is the string `YYYY-MM-DD` and nothing else. Arithmetic on one is done at
 * UTC midnight, where every day is 24 hours and no DST transition can shorten it — the anchor is an
 * implementation detail that never escapes, because what goes in and comes out is the string.
 */

/** Today's date on a given clock. Falls back to the viewer's zone only while the business loads. */
export function todayIn(zone: string | undefined): string {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: zone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date());
}

/** The calendar date an instant falls on, on a given clock. */
export function dateOfInstantIn(instant: string, zone: string | undefined): string {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: zone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date(instant));
}

/** UTC midnight of a plain date — the anchor arithmetic and formatting hang off. */
function anchor(plainDate: string): Date {
  return new Date(`${plainDate}T00:00:00Z`);
}

export function addDays(plainDate: string, days: number): string {
  const date = anchor(plainDate);
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

/** Monday of the week containing the date. */
export function startOfWeek(plainDate: string): string {
  const date = anchor(plainDate);
  // getUTCDay is Sunday-first; shift so Monday is 0.
  return addDays(plainDate, -((date.getUTCDay() + 6) % 7));
}

/**
 * Formats a plain date for display.
 *
 * <p>Pinned to UTC deliberately. The anchor is UTC midnight, so formatting it on any other clock
 * would shift it back to the neighbouring day and reintroduce the defect at the last step — after
 * the arithmetic had been done correctly.
 */
export function formatPlainDate(
  plainDate: string,
  options: Intl.DateTimeFormatOptions,
): string {
  return new Intl.DateTimeFormat("en-GB", { ...options, timeZone: "UTC" }).format(
    anchor(plainDate),
  );
}
