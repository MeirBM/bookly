import { expect, test } from "@playwright/test";
import {
  toGoogleCalendarUrl,
  toIcsDataUrl,
  toIcsFile,
  type CalendarEvent,
} from "../src/lib/calendar-links";

/**
 * Turn-4 criteria 4.2, 4.3 and 4.4 — the calendar hand-off, as pure functions.
 *
 * <p>This is the `IcsFileTest` the spec names. It is a Playwright spec file only because Playwright
 * is the runner this project has; nothing in it touches `page`, opens a browser or speaks to the
 * API. Adding a second test runner to get a nicer `describe` would be a new dependency for no new
 * coverage.
 *
 * <p>Written from `docs/spec/turn-4.md`. The author has not read `frontend/src`; the export
 * signatures were taken from a grep for `export` in `src/lib/calendar-links.ts`, and what the
 * functions must *return* is taken from the specification, never from their bodies.
 *
 * <p>The event is built the way section 3 describes the feature — "both are generated from one
 * function so the two exports cannot drift" — so every assertion below is about a file or a URL
 * derived from one `CalendarEvent`, and 4.3 compares the two against each other rather than against
 * two separately hard-coded expectations that could both be wrong together.
 */

// ----------------------------------------------------------------------------------- the fixture

/**
 * A booking in Auckland (UTC+12 in September), which is the point.
 *
 * <p>09:00 on the 24th in Auckland is 21:00 on the *23rd* in UTC: the hour, the minute and the
 * calendar day all differ. A builder that formatted the wall clock it was handed rather than the
 * instant would produce `20260924T090000Z`, which is a different moment, on a different day, and
 * criterion 4.4 exists because that file looks entirely plausible to whoever wrote it.
 */
const SERVICE = "Signature Trim";
const BUSINESS = "Browser Salon";
const EMPLOYEE = "Robin Cutter";

const AUCKLAND_EVENT: CalendarEvent = {
  title: SERVICE,
  description: `${SERVICE} with ${EMPLOYEE}`,
  location: BUSINESS,
  startsAt: "2026-09-24T09:00:00+12:00",
  endsAt: "2026-09-24T09:45:00+12:00",
};

/** The same two instants, stated in UTC. `2026-09-24T09:00+12:00` is `2026-09-23T21:00Z`. */
const EXPECTED_START_UTC = "20260923T210000Z";
const EXPECTED_END_UTC = "20260923T214500Z";

const UID = "bookly-appointment-11111111-2222-3333-4444-555555555555";

/** Any iCalendar UTC date-time: `YYYYMMDDTHHMMSSZ`. */
const UTC_STAMP = /\d{8}T\d{6}Z/g;

/**
 * Runs `build` with the process pinned to `zone`, then puts the zone back.
 *
 * <p>This is how 4.4 is actually decided rather than assumed. These functions run in Node, not in
 * the browser, so `test.use({ timezoneId })` does not reach them — the "viewer's zone" here is the
 * zone of whatever machine runs the suite. Pinning it to two zones a day apart and requiring the
 * *same* bytes out of both is the assertion: an implementation that read a local clock cannot
 * satisfy it, and one that formats the instant cannot fail it. Without this the test would pass
 * vacuously on any runner that happened to be set to UTC, which is most CI.
 */
function inViewerZone<T>(zone: string, build: () => T): T {
  const before = process.env.TZ;
  process.env.TZ = zone;
  try {
    return build();
  } finally {
    if (before === undefined) {
      delete process.env.TZ;
    } else {
      process.env.TZ = before;
    }
  }
}

/** The unfolded content lines of an ics file, as a calendar client reads them. */
function contentLines(ics: string): string[] {
  return ics.split("\r\n").filter((line) => line.length > 0);
}

/** RFC 5545 TEXT unescaping, so a round trip compares the value rather than its encoding. */
function unescapeText(value: string): string {
  return value.replace(/\\([nN])/g, "\n").replace(/\\([,;\\])/g, "$1");
}

/** RFC 5545 unfolding: a line beginning with a space or tab continues the one before it. */
function unfold(ics: string): string {
  return ics.replace(/\r\n[ \t]/g, "");
}

/** The value of a property, after unfolding, e.g. `DTSTART` -> `20260923T210000Z`. */
function property(ics: string, name: string): string | undefined {
  const line = contentLines(unfold(ics)).find(
    (candidate) => candidate === name || candidate.startsWith(`${name}:`) || candidate.startsWith(`${name};`),
  );
  if (line === undefined) return undefined;
  const colon = line.indexOf(":");
  return colon === -1 ? "" : line.slice(colon + 1);
}

// -------------------------------------------------------------------------------------------- 4.2

test.describe("IcsFileTest", () => {
  test("theIcsNamesTheServiceBusinessEmployeeAndTheExactStartAndEndInUtc", () => {
    const ics = toIcsFile(AUCKLAND_EVENT, UID);

    // It has to be an iCalendar object at all, or nothing below means anything.
    expect(ics, "an .ics file opens with BEGIN:VCALENDAR").toContain("BEGIN:VCALENDAR");
    expect(ics, "and closes it").toContain("END:VCALENDAR");
    expect(ics, "with an event inside").toContain("BEGIN:VEVENT");
    expect(ics, "which is closed").toContain("END:VEVENT");
    expect(
      property(ics, "VERSION"),
      "clients dispatch on VERSION; a file without one is refused without explanation",
    ).toBe("2.0");
    expect(
      property(ics, "UID"),
      "the uid the caller supplied identifies this appointment, so importing the file twice " +
        "updates one entry instead of creating two. Asserted as a substring: RFC 5545 wants a " +
        "globally unique id and appending a domain to make one is conventional, and the spec " +
        "says nothing either way — what must hold is that the caller's uid is what identifies it",
    ).toContain(UID);

    // --- names the service, the business and the employee.
    //
    // Deliberately asserted against the whole file rather than against a named property: the
    // specification says the file must *name* these three things and does not say which of
    // SUMMARY, DESCRIPTION and LOCATION carries which, so pinning that here would be inventing a
    // contract the spec never stated.
    const unfolded = unescapeText(unfold(ics));
    expect(unfolded, "4.2: the file names the service").toContain(SERVICE);
    expect(unfolded, "4.2: and the business").toContain(BUSINESS);
    expect(
      unfolded,
      "4.2: and the employee — a customer with three haircuts booked needs to know which one this " +
        "is and who it is with",
    ).toContain(EMPLOYEE);
    expect(
      property(ics, "SUMMARY"),
      "the calendar entry needs a title, which is what every client shows in the grid",
    ).toBeTruthy();

    // --- the exact start and end, in UTC.
    expect(
      property(ics, "DTSTART"),
      "4.2 and pitfall 2: DTSTART must be the instant in UTC, in the compact form with a trailing " +
        "Z. The Z is not decoration — without it the value is a *floating* time, which every " +
        "client interprets in whatever zone the reader's machine is set to, so the appointment " +
        "lands at 09:00 wherever the customer happens to be rather than at 09:00 in Auckland",
    ).toBe(EXPECTED_START_UTC);
    expect(
      property(ics, "DTEND"),
      "and DTEND likewise, so the entry occupies the length of the service rather than a default " +
        "the client picked",
    ).toBe(EXPECTED_END_UTC);
    expect(
      unfolded,
      "the local wall-clock reading must not appear as a timestamp anywhere: 20260924T090000 is " +
        "the same appointment written as the wrong instant",
    ).not.toMatch(/20260924T0900/);
  });

  test("theIcsUsesCrlfLineEndingsAndFoldsLongLines", () => {
    const longDescription =
      `${SERVICE} with ${EMPLOYEE} at ${BUSINESS}. ` +
      "Please arrive five minutes early so we can get you settled, and let us know in advance if " +
      "you need to move this appointment to another time.";
    const ics = toIcsFile({ ...AUCKLAND_EVENT, description: longDescription }, UID);

    // Pitfall 1: a client that rejects a malformed file does so silently, and the customer
    // concludes the button is broken rather than that the file is.
    expect(ics, "RFC 5545 lines end CRLF").toContain("\r\n");
    expect(
      ics.replace(/\r\n/g, ""),
      "and every line ends CRLF — a bare LF anywhere makes the file non-conforming, and the " +
        "clients that refuse it say nothing",
    ).not.toContain("\n");

    for (const line of contentLines(ics)) {
      expect(
        Buffer.byteLength(line, "utf8"),
        `no content line may exceed 75 octets; this one does, so a strict client rejects the ` +
          `whole file: ${JSON.stringify(line)}`,
      ).toBeLessThanOrEqual(75);
    }
    expect(
      unescapeText(property(ics, "DESCRIPTION") ?? ""),
      "and folding must be reversible: unfolding the file has to give back the text that was put " +
        "in, or the fold silently corrupted the description",
    ).toBe(longDescription);
  });

  test("escapesTheCharactersThatWouldOtherwiseBreakTheFile", () => {
    // Service and business names are free text an owner typed, so commas and semicolons are
    // ordinary, not hostile. In iCalendar both are value separators: unescaped, they truncate the
    // property, which is pitfall 1's silent rejection arriving through the front door.
    const messy = "Cut, Colour & Blow-dry; the works";
    const ics = toIcsFile({ ...AUCKLAND_EVENT, title: messy }, UID);
    const summary = property(unfold(ics), "SUMMARY") ?? "";

    expect(summary, "a comma in a name is escaped, not left to split the value").toContain("\\,");
    expect(summary, "and a semicolon likewise").toContain("\\;");
    expect(
      summary.replace(/\\([,;\\])/g, "$1"),
      "and unescaping gives the owner's text back unchanged",
    ).toBe(messy);
  });

  // ------------------------------------------------------------------------------------------ 4.3

  test("googleLinkAgreesWithTheIcsFile", () => {
    const ics = toIcsFile(AUCKLAND_EVENT, UID);
    const url = toGoogleCalendarUrl(AUCKLAND_EVENT);

    expect(url, "the Google hand-off is a link to Google Calendar").toMatch(
      /^https:\/\/([a-z]+\.)*google\.com\//,
    );
    expect(
      url,
      "4.1: a link that needed an account would go to an OAuth consent screen; this one must not",
    ).not.toMatch(/accounts\.google\.com|oauth|client_id/i);

    const inTheLink = url.match(UTC_STAMP) ?? [];
    expect(
      inTheLink,
      "the link has to say when the appointment is, in the same UTC form the file uses",
    ).toContain(property(ics, "DTSTART"));
    expect(inTheLink, "and when it ends").toContain(property(ics, "DTEND"));
    expect(
      inTheLink,
      "4.3: the two exports cannot be allowed to disagree — a customer who used one button and a " +
        "business reading the other would be looking at different times for the same booking",
    ).toEqual([EXPECTED_START_UTC, EXPECTED_END_UTC]);

    expect(
      decodeURIComponent(url.replace(/\+/g, " ")),
      "and the link names the same thing the file does, so the entry is recognisable either way",
    ).toContain(SERVICE);
  });

  test("googleLinkAgreesWithTheIcsFileForAHalfHourOffsetZone", () => {
    // Adelaide is UTC+09:30. An implementation that carried the offset in whole hours is right
    // twice a day everywhere else and wrong by thirty minutes here.
    const adelaide: CalendarEvent = {
      ...AUCKLAND_EVENT,
      startsAt: "2026-09-24T09:00:00+09:30",
      endsAt: "2026-09-24T09:45:00+09:30",
    };
    const ics = toIcsFile(adelaide, UID);
    const url = toGoogleCalendarUrl(adelaide);

    expect(property(ics, "DTSTART"), "09:00 at +09:30 is 23:30 the previous day in UTC").toBe(
      "20260923T233000Z",
    );
    expect(property(ics, "DTEND")).toBe("20260924T001500Z");
    expect(
      url.match(UTC_STAMP) ?? [],
      "and the Google link carries exactly those two, across the midnight the end crosses",
    ).toEqual(["20260923T233000Z", "20260924T001500Z"]);
  });

  // ------------------------------------------------------------------------------------------ 4.4

  test("exportsTheBusinessTimeNotTheViewers", () => {
    // Two viewers a full day apart: Kiritimati is UTC+14, Midway is UTC-11. The booking is the
    // same booking, in Auckland, in both cases.
    const kiritimati = inViewerZone("Pacific/Kiritimati", () => ({
      ics: toIcsFile(AUCKLAND_EVENT, UID),
      google: toGoogleCalendarUrl(AUCKLAND_EVENT),
    }));
    const midway = inViewerZone("Pacific/Midway", () => ({
      ics: toIcsFile(AUCKLAND_EVENT, UID),
      google: toGoogleCalendarUrl(AUCKLAND_EVENT),
    }));

    expect(
      property(kiritimati.ics, "DTSTART"),
      "4.4: the file must carry the instant the business booked, whatever clock the customer's " +
        "machine is set to",
    ).toBe(EXPECTED_START_UTC);
    expect(property(kiritimati.ics, "DTEND")).toBe(EXPECTED_END_UTC);

    expect(
      midway.ics,
      "and two viewers 25 hours apart must get byte-identical files: any difference means the " +
        "customer's own clock leaked into the export, and the appointment moves depending on who " +
        "pressed the button",
    ).toBe(kiritimati.ics);
    expect(midway.google, "the Google link is subject to exactly the same rule").toBe(
      kiritimati.google,
    );

    // The two zones' own readings of this instant, which is what a leak would have produced.
    expect(kiritimati.ics, "11:00 on the 24th is Kiritimati's reading, not the business's").not.toMatch(
      /20260924T1100/,
    );
    expect(midway.ics, "10:00 on the 23rd is Midway's reading, not the business's").not.toMatch(
      /20260923T1000/,
    );
  });

  test("aBookingAcrossADstBoundaryExportsTheInstantItActuallyHappensAt", () => {
    // New Zealand moves to daylight time on 27 September 2026, so a booking the following week is
    // at +13, not +12. The offset comes from the instant supplied, so a builder that subtracted a
    // hard-coded twelve hours is wrong for half the year.
    const afterTheChange: CalendarEvent = {
      ...AUCKLAND_EVENT,
      startsAt: "2026-09-30T09:00:00+13:00",
      endsAt: "2026-09-30T09:45:00+13:00",
    };
    const ics = toIcsFile(afterTheChange, UID);

    expect(property(ics, "DTSTART"), "09:00 at +13:00 is 20:00 the previous day in UTC").toBe(
      "20260929T200000Z",
    );
    expect(property(ics, "DTEND")).toBe("20260929T204500Z");
  });

  // ----------------------------------------------------- 4.1's client-side half, as a pure function

  test("theIcsDataUrlCarriesTheFileItselfRatherThanAnAddressToFetchItFrom", () => {
    const ics = toIcsFile(AUCKLAND_EVENT, UID);
    const dataUrl = toIcsDataUrl(AUCKLAND_EVENT, UID);

    expect(
      dataUrl,
      "4.1: the Apple/Outlook hand-off must need no backend call, so the file travels in the URL",
    ).toMatch(/^data:text\/calendar[;,]/);

    const comma = dataUrl.indexOf(",");
    const meta = dataUrl.slice(0, comma);
    const payload = dataUrl.slice(comma + 1);
    const decoded = meta.includes(";base64")
      ? Buffer.from(payload, "base64").toString("utf8")
      : decodeURIComponent(payload);

    expect(
      decoded,
      "and what it carries is the same file, not a second one built by a different path that " +
        "could disagree with it",
    ).toBe(ics);
  });
});
