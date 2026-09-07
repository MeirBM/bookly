import { expect, test, type Page } from "@playwright/test";
import {
  API,
  bookViaPublicApi,
  localTimeIn,
  newOwner,
  ownersAppointments,
  publicAvailability,
  seedBookable,
  seedUnbookable,
  upcoming,
  type Seeded,
} from "./support/fixtures";

/**
 * Turn-3 criteria 3.18, 3.19 and 3.20: the public booking page.
 *
 * Written from `docs/spec/turn-3.md` and `docs/api/turn-3-openapi.json`; frontend/src was not read.
 * Every business is seeded through the authenticated API, so each test owns its own data.
 *
 * The browser is pinned to a zone deliberately unlike the business's. What a page prints is an
 * instant formatted in *some* zone, and the whole of criterion 3.22 — and half of 3.18 — is about
 * which one. Leaving the viewer's zone to the machine running the suite would let a page that
 * formats in the viewer's clock pass here and be wrong in Auckland.
 */

/** A Thursday, comfortably ahead of "now" and inside the booking horizon (3.28). */
const DATE = upcoming("THURSDAY");

/** A Monday: the 3.20 fixture rosters Thursdays only, so this is a day with genuinely nothing on. */
const DAY_NOBODY_WORKS = upcoming("MONDAY");

const NOT_FOUND_TEXT = /nothing to book|no business taking bookings/i;
// Deliberately not just /booked/: the confirmation says "You are booked", so a matcher containing
// that word alone would be satisfied by the very page this criterion forbids.
const TAKEN_TEXT =
  /someone|somebody|no longer (free|available)|already (taken|booked)|just (been )?(taken|booked)/i;
const CONFIRMED_TEXT = /you are booked|booking confirmed|reference/i;
const ERROR_TEXT = /could not|couldn't|went wrong|failed|try again/i;
const NO_TIMES_TEXT = /no free times|no available times|nothing free|try another day/i;
const SETTLE = 20_000;

test.use({ timezoneId: "America/Los_Angeles" });

async function bodyText(page: Page): Promise<string> {
  await expect(page.getByText(/loading/i)).toHaveCount(0, { timeout: SETTLE });
  return page.locator("body").innerText();
}

/** Every time the page is currently offering, as the labels a visitor would click. */
function slotButtons(page: Page) {
  return page.getByRole("button", { name: /^\s*\d{1,2}:\d{2}\s*$/ });
}

/**
 * Runs an action that changes what the page is asking for, and waits for the answer to arrive.
 *
 * <p>Waiting on the availability response rather than on the absence of a "loading" label is what
 * makes the steps below reliable: changing the service or the person re-renders the slot list, and
 * a click that lands on the list being replaced is a click that quietly does nothing.
 */
async function whileSlotsReload(page: Page, action: () => Promise<unknown>) {
  // Tolerant on purpose: selecting the value a control already holds asks the server nothing, and
  // a helper that insisted on a request would hang on the step that changed nothing.
  const answered = page
    .waitForResponse(
      (response) => response.url().includes("/availability") && response.request().method() === "GET",
      { timeout: 5_000 },
    )
    .catch(() => null);
  await action();
  await answered;
  await expect(slotButtons(page).first().or(page.getByText(NO_TIMES_TEXT)).first())
    .toBeVisible({ timeout: SETTLE });
}

/**
 * Opens the page, chooses the date, and waits for the day to have settled into either times or a
 * statement that there are none — so a test never reads the slot list while it is still arriving.
 */
async function openBookingPage(page: Page, seeded: Seeded, date: string): Promise<string[]> {
  await page.goto(`/book/${seeded.slug}`);
  await expect(page.getByText(new RegExp(seeded.businessName.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"))))
    .toBeVisible({ timeout: SETTLE });
  await whileSlotsReload(page, () => page.locator("input[type=date]").fill(date));
  await expect(slotButtons(page).first().or(page.getByText(NO_TIMES_TEXT)).first())
    .toBeVisible({ timeout: SETTLE });
  return slotButtons(page).allInnerTexts();
}

test.describe("public booking page", () => {
  test.beforeAll(async ({ request }) => {
    const health = await request.get(`${API}/actuator/health`).catch(() => null);
    expect(health?.ok(), `these tests need the backend at ${API}`).toBe(true);
  });

  /**
   * 3.18 — the whole flow, ending in an appointment that exists rather than a page that says so.
   *
   * The last two assertions are the ones that make this more than a screenshot: the confirmation is
   * checked against the *business's* clock, and the appointment is then read back through the
   * owner's API. A page that printed a plausible confirmation and posted nothing would pass every
   * assertion above them and fail these.
   */
  test("aVisitorCanBookFromStartToFinish", async ({ page, request }) => {
    const owner = await newOwner(request);
    // Two people, so choosing one of them is a real step rather than a formality.
    const seeded = await seedBookable(request, owner, { people: 2, durationMinutes: 30 });

    const offered = await openBookingPage(page, seeded, DATE);
    expect(offered.length, "a nine-to-five Thursday offers times").toBeGreaterThan(0);

    // service → person
    const [serviceSelect, personSelect] = [page.locator("select").nth(0), page.locator("select").nth(1)];
    await whileSlotsReload(page, () => serviceSelect.selectOption(seeded.serviceId));
    await whileSlotsReload(page, () => personSelect.selectOption(seeded.employeeId));
    await expect(slotButtons(page).first()).toBeVisible({ timeout: SETTLE });

    // date → slot
    const availability = await publicAvailability(request, seeded.slug, seeded.serviceId, DATE, seeded.employeeId);
    expect(availability.slots.length, "the engine offers times for the chosen person").toBeGreaterThan(0);
    const chosen = availability.slots[0].start;
    const chosenLabel = localTimeIn(chosen, availability.timezone);
    await page.getByRole("button", { name: chosenLabel, exact: true }).click();

    // details
    await page.getByLabel(/name/i).fill("Casey Visitor");
    const customerEmail = `casey-${Date.now()}@example.test`;
    await page.getByLabel(/email/i).fill(customerEmail);
    await page.getByLabel(/phone/i).fill("+64211234567");
    await page.locator("button[type=submit]").click();

    // confirmation
    await expect(page.getByText(CONFIRMED_TEXT).first(), "the booking is confirmed on screen")
      .toBeVisible({ timeout: SETTLE });
    const confirmation = await page.locator("body").innerText();
    expect(confirmation, "the visitor is told plainly that they are booked").toMatch(CONFIRMED_TEXT);
    expect(confirmation, "and what they booked").toContain(seeded.serviceName);
    expect(confirmation, "and who with — they chose a person, so it must be that person")
      .toContain(seeded.employeeName);
    expect(confirmation, `the time on the business's clock (${chosenLabel} in ${seeded.timezone})`)
      .toContain(chosenLabel);
    expect(
      confirmation,
      "pitfall 8: the confirmation states the zone it is speaking in, so a visitor in another " +
        "country knows which clock the time refers to",
    ).toContain(seeded.timezone);

    const stored = await ownersAppointments(request, owner, seeded.businessId, DATE, DATE);
    expect(stored.length, "the booking exists, not merely the page that congratulated the visitor")
      .toBe(1);
    expect(stored[0].startsAt, "at the instant the page offered").toBe(chosen);
    expect(stored[0].employeeId, "with the person the visitor picked").toBe(seeded.employeeId);
    expect(stored[0].customerEmail, "for the visitor who typed their details").toBe(customerEmail);
  });

  /**
   * 3.19 — a slot taken between page load and submit.
   *
   * <p><strong>This is a real race, not an intercepted response.</strong> The browser loads the
   * times and the visitor opens the details form, and only then does another visitor take that
   * exact slot through the public API — the same route a second browser would have used. Nothing is
   * stubbed, so what is asserted is the behaviour of the page against the real server: an
   * intercepted 409 would test our own error handler against a fixture of our own making, and would
   * still pass if the server never sent that code.
   *
   * <p>Three things must hold, and the third is the one that is easy to miss: the visitor must not
   * be congratulated, they must be told what happened, and the times on screen must be refreshed so
   * that trying again does not repeat the same failure.
   */
  test("aSlotTakenWhileBookingIsReportedNotSwallowed", async ({ page, request }) => {
    const owner = await newOwner(request);
    const seeded = await seedBookable(request, owner, { durationMinutes: 30 });

    await openBookingPage(page, seeded, DATE);
    const availability = await publicAvailability(request, seeded.slug, seeded.serviceId, DATE);
    const contested = availability.slots[0].start;
    const contestedLabel = localTimeIn(contested, availability.timezone);

    // The visitor opens the details form: from here their page is a stale picture of the day.
    await page.getByRole("button", { name: contestedLabel, exact: true }).click();
    await page.getByLabel(/name/i).fill("Slow Typist");
    await page.getByLabel(/email/i).fill(`slow-${Date.now()}@example.test`);

    // While they type, somebody else takes the same time.
    const raced = await bookViaPublicApi(request, seeded.slug, {
      serviceId: seeded.serviceId,
      employeeId: seeded.employeeId,
      startsAt: contested,
      name: "Faster Visitor",
    });
    expect(raced.status(), "the out-of-band booking is what creates the race").toBe(201);

    await page.locator("button[type=submit]").click();

    await expect(
      page.getByText(TAKEN_TEXT).first(),
      "the visitor must be told what happened — a submit that does nothing visible is the silent " +
        "failure this criterion exists to forbid",
    ).toBeVisible({ timeout: SETTLE });
    const afterSubmit = await page.locator("body").innerText();

    expect(
      afterSubmit,
      "a visitor whose slot was taken must never be shown a confirmation: they would arrive to a " +
        "shop with no room for them, holding a page that said it was fine",
    ).not.toMatch(CONFIRMED_TEXT);
    await expect(
      page.getByRole("button", { name: contestedLabel, exact: true }),
      `${contestedLabel} is gone now, so the times on screen must no longer offer it; leaving it ` +
        "there invites the visitor to fail again in exactly the same way",
    ).toHaveCount(0, { timeout: SETTLE });

    const stored = await ownersAppointments(request, owner, seeded.businessId, DATE, DATE);
    expect(stored.length, "the loser's submit created nothing").toBe(1);
    expect(stored[0].customerName, "the winner is the visitor who got there first")
      .toContain("Faster Visitor");
  });

  /**
   * 3.20 — four distinguishable states, and the not-found state that 3.17 requires two different
   * causes to share.
   *
   * <p>The error state is staged by failing the public API, which is the only way to make a server
   * fault happen on demand. That interception is an honest one: it does not stand in for the
   * server's behaviour, it removes it, and what is asserted is only how the page responds to a
   * request that failed.
   */
  test("3.20 the public page renders four distinguishable states", async ({ page, request }) => {
    const owner = await newOwner(request);
    // Thursdays only, so any other weekday is genuinely empty rather than broken.
    const seeded = await seedBookable(request, owner, {
      hours: [{ weekday: "THURSDAY", start: "09:00:00", end: "17:00:00" }],
    });

    // --- loading: hold the API open and look while it is still open.
    await page.route("**/api/public/**", async (route) => {
      await new Promise((resolve) => setTimeout(resolve, 1500));
      try {
        await route.continue();
      } catch {
        // The page moved on while this was held; nothing left to continue.
      }
    });
    try {
      await page.goto(`/book/${seeded.slug}`);
      await expect(
        page.getByText(/loading/i).first(),
        "a request that has not answered yet must say so, or the page looks broken while it works",
      ).toBeVisible({ timeout: SETTLE });
    } finally {
      await page.unrouteAll({ behavior: "ignoreErrors" });
    }

    // --- content
    const offered = await openBookingPage(page, seeded, DATE);
    expect(offered.length, "a Thursday has times").toBeGreaterThan(0);
    const contentText = await bodyText(page);

    // --- empty: a real question with an empty answer.
    await whileSlotsReload(page, () => page.locator("input[type=date]").fill(DAY_NOBODY_WORKS));
    const emptyText = await bodyText(page);
    expect(
      emptyText,
      "a day with nothing free must say so; an empty area leaves the visitor to guess whether " +
        "the page is broken",
    ).toMatch(NO_TIMES_TEXT);

    // --- error
    await page.route("**/api/public/**", (route) =>
      route.fulfill({
        status: 500,
        contentType: "application/json",
        body: JSON.stringify({ code: "BOOM", message: "boom" }),
      }),
    );
    let errorText: string;
    try {
      await page.goto(`/book/${seeded.slug}`);
      errorText = await bodyText(page);
    } finally {
      await page.unrouteAll({ behavior: "ignoreErrors" });
    }
    expect(
      errorText,
      "a failure must look like a failure. Telling a visitor there is no business at this address " +
        "when the server is broken sends away a customer the business would have had, and the " +
        "owner never learns it happened",
    ).toMatch(ERROR_TEXT);
    expect(
      errorText,
      "and a server fault must not be dressed as a business that does not exist — that is the " +
        "not-found state, and 3.20 asks for four states a reader can tell apart",
    ).not.toMatch(NOT_FOUND_TEXT);

    // --- and the settled states are actually distinguishable from one another.
    expect(emptyText, "empty must not read as content").not.toEqual(contentText);
    expect(errorText, "error must not read as empty").not.toEqual(emptyText);
    expect(errorText, "error must not read as content").not.toEqual(contentText);
  });

  /**
   * 3.20, the fifth state — and 3.17 in the browser. A business nobody can book must be
   * indistinguishable from an address that was never a business, because telling the two apart
   * turns the slug space into a directory of who has an account.
   */
  test("3.20 an unbookable business and an unknown address share one not-found state", async ({
    page,
    request,
  }) => {
    const owner = await newOwner(request);
    const unbookable = await seedUnbookable(request, owner);

    await page.goto(`/book/${unbookable.slug}`);
    const unbookableText = await bodyText(page);
    await page.goto(`/book/never-existed-${Date.now()}`);
    const unknownText = await bodyText(page);

    expect(unbookableText, "a business with nobody able to serve shows the not-found state")
      .toMatch(NOT_FOUND_TEXT);
    expect(unknownText, "and so does an address that never existed").toMatch(NOT_FOUND_TEXT);
    expect(
      unbookableText,
      "the two must read identically: any difference tells a stranger which slugs are real",
    ).toEqual(unknownText);
    expect(unbookableText, "and neither may name the business it declined to describe")
      .not.toContain(unbookable.slug);
  });
});

/**
 * Turn-4 criteria 4.1, 4.12 and 4.14 — the calendar hand-off on a real confirmation, and the
 * business's identity on the public page.
 *
 * <p>Written from `docs/spec/turn-4.md`; frontend/src was not read. The exports of
 * `src/lib/calendar-links.ts` are exercised directly by `ics-file.spec.ts`; what is decided here is
 * only what a customer's browser actually does with them.
 */

/** `2026-09-23T21:00:00.000Z` -> `20260923T210000Z`, the form an .ics and a Google link both use. */
function utcStamp(instant: string): string {
  return new Date(instant).toISOString().replace(/[-:]/g, "").replace(/\.\d{3}/, "");
}

/** The text of an `.ics` carried in a data: URL, however it was encoded. */
function decodeDataUrl(dataUrl: string): string {
  const comma = dataUrl.indexOf(",");
  const meta = dataUrl.slice(0, comma);
  const payload = dataUrl.slice(comma + 1);
  return meta.includes(";base64")
    ? Buffer.from(payload, "base64").toString("utf8")
    : decodeURIComponent(payload);
}

/** A 1x1 PNG, so an intercepted logo request answers with an image that genuinely decodes. */
const ONE_PIXEL_PNG =
  "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";

/** PUT /api/businesses/{businessId}/logo as the owner. */
async function setLogo(
  request: import("@playwright/test").APIRequestContext,
  owner: { auth: Record<string, string> },
  businessId: string,
  logoUrl: string | null,
) {
  const response = await request.put(`${API}/api/businesses/${businessId}/logo`, {
    data: { logoUrl },
    headers: owner.auth,
  });
  expect(response.status(), `setting the logo to ${logoUrl}`).toBe(200);
  return response.json();
}

/**
 * The Bookly mark, as an accessible image.
 *
 * <p>4.12 and 4.14 both ask for a *mark* rather than a gap, and a mark a screen reader cannot name
 * is a gap to the people most likely to be harmed by one. `role=img` with a name mentioning Bookly
 * is how both an `<img alt>` and an inline `<svg role="img" aria-label>` expose that; the testid is
 * accepted as an alternative so the criterion is not decided by which of the two the page chose.
 */
function booklyMark(page: Page) {
  return page
    .getByRole("img", { name: /bookly/i })
    .or(page.locator('[data-testid="bookly-mark"]'))
    .first();
}

/** Every `<img>` the browser tried and failed to load: loaded, but with nothing in it. */
async function brokenImages(page: Page): Promise<string[]> {
  return page.evaluate(() =>
    [...document.querySelectorAll("img")]
      .filter((img) => img.complete && img.naturalWidth === 0)
      .map((img) => img.currentSrc || img.getAttribute("src") || "(no src)"),
  );
}

test.describe("turn 4 — calendar hand-off and business identity", () => {
  test.beforeAll(async ({ request }) => {
    const health = await request.get(`${API}/actuator/health`).catch(() => null);
    expect(health?.ok(), `these tests need the backend at ${API}`).toBe(true);
  });

  /**
   * 4.1 — the confirmation offers Google and Apple/Outlook, and neither needs an account, a
   * permission or a backend call.
   *
   * <p>The negative half is the half that matters. Two controls that *look* like calendar buttons
   * are easy; what section 3 requires is that both are inert client-side artefacts — a URL and a
   * few lines of text — so the assertions are about where each control points and about the
   * traffic the page does not generate. A hand-off that fetched the file from Bookly would pass a
   * screenshot review and fail here.
   *
   * <p>The contents are checked against the booking that was actually made, so this is also 4.2
   * end to end: `ics-file.spec.ts` proves the builder is right about an event handed to it, and
   * this proves the page hands it the right event.
   */
  test("aConfirmedBookingCanBeAddedToACalendar", async ({ page, request }) => {
    test.setTimeout(120_000);
    const owner = await newOwner(request);
    const seeded = await seedBookable(request, owner, { durationMinutes: 30 });

    await openBookingPage(page, seeded, DATE);
    const availability = await publicAvailability(request, seeded.slug, seeded.serviceId, DATE);
    expect(availability.slots.length, "the seeded day offers times").toBeGreaterThan(0);
    const chosen = availability.slots[0].start;
    const chosenEnd = availability.slots[0].end;
    await page.getByRole("button", { name: localTimeIn(chosen, availability.timezone), exact: true }).click();

    await page.getByLabel(/name/i).fill("Calendar Keeper");
    await page.getByLabel(/email/i).fill(`calendar-${Date.now()}@example.test`);
    await page.getByLabel(/phone/i).fill("+64211234567");
    await page.locator("button[type=submit]").click();
    await expect(page.getByText(CONFIRMED_TEXT).first(), "the booking is confirmed on screen")
      .toBeVisible({ timeout: SETTLE });

    // From here on, anything the page asks the server for is a backend call the criterion forbids.
    const backendCalls: string[] = [];
    page.on("request", (req) => {
      if (req.url().startsWith(API)) backendCalls.push(`${req.method()} ${req.url()}`);
    });

    // --- the two controls exist and are offered to the customer.
    const google = page.locator("a[href]").filter({ hasText: /google/i }).first();
    const appleOutlook = page
      .locator("a[href]")
      .filter({ hasText: /apple|outlook|\.ics|ical|download/i })
      .first();
    await expect(
      google,
      "4.1: the confirmation offers Google Calendar — a customer who has to retype the time is a " +
        "customer who may retype it wrong, or not at all",
    ).toBeVisible({ timeout: SETTLE });
    await expect(
      appleOutlook,
      "4.1: and Apple/Outlook, which is the .ics file — offering only Google excludes everyone who " +
        "does not use it",
    ).toBeVisible({ timeout: SETTLE });

    const googleHref = (await google.getAttribute("href")) ?? "";
    const icsHref = (await appleOutlook.getAttribute("href")) ?? "";

    // --- neither needs an account, a permission or a backend call.
    expect(googleHref, "the Google hand-off is a plain link to Google Calendar").toMatch(
      /^https:\/\/([a-z]+\.)*google\.com\//,
    );
    expect(
      googleHref,
      "and not an OAuth flow: an account, a consent screen and a stored token are exactly what " +
        "section 3 says this feature must not need",
    ).not.toMatch(/accounts\.google\.com|oauth|client_id|scope=/i);
    expect(
      icsHref,
      "the .ics travels in the page itself, as a data: URL. An https link back to Bookly would be " +
        "a backend call, and a route to build and serve a file the browser already has",
    ).toMatch(/^(data:text\/calendar[;,]|blob:)/);
    expect(
      icsHref.startsWith(API),
      "and in particular it must not point at the API",
    ).toBe(false);

    // Give the page a moment to make any request it was going to make.
    await page.waitForTimeout(2_000);
    expect(
      backendCalls,
      "4.1: neither hand-off may need a backend call — an .ics is a few lines of text and a Google " +
        "link is a URL, and a confirmation that phones home to produce them fails for every " +
        "customer whose booking succeeded but whose network then did not",
    ).toEqual([]);

    // --- and what they hand off is this booking, not a plausible-looking other one.
    expect(icsHref.startsWith("data:"), "the file must be readable from the page for this check").toBe(true);
    const ics = decodeDataUrl(icsHref);
    expect(ics, "the file is an iCalendar object").toContain("BEGIN:VEVENT");
    expect(ics, "4.2: naming the service that was booked").toContain(seeded.serviceName);
    expect(ics, "the business it was booked with").toContain(seeded.businessName);
    expect(ics, "and the person who will do it").toContain(seeded.employeeName);
    expect(
      ics,
      `4.2: at the instant the API recorded, in UTC (${utcStamp(chosen)}) — this browser is in ` +
        "America/Los_Angeles and the business is in " +
        `${seeded.timezone}, so a file built from the viewer's clock says something else`,
    ).toContain(utcStamp(chosen));
    expect(ics, "and ending when the service ends").toContain(utcStamp(chosenEnd));

    const stamps = googleHref.match(/\d{8}T\d{6}Z/g) ?? [];
    expect(
      stamps,
      "4.3 on a real booking: the Google link and the file must agree about when the appointment is",
    ).toEqual([utcStamp(chosen), utcStamp(chosenEnd)]);
  });

  /**
   * 4.12 — a business with no logo shows the Bookly mark rather than a gap.
   *
   * <p>A gap is not neutral. A booking page that renders a blank rectangle where the identity
   * belongs reads as a page that failed to load, and section 1's reason for this feature is that a
   * customer is being asked to trust an address.
   */
  test("aBusinessWithoutALogoShowsTheBooklyMark", async ({ page, request }) => {
    const owner = await newOwner(request);
    const seeded = await seedBookable(request, owner, { durationMinutes: 30 });

    // The premise, asserted rather than assumed: this business really has no logo.
    const publicView = await request.get(`${API}/api/public/businesses/${seeded.slug}`);
    expect(publicView.status(), "the public view of the seeded business").toBe(200);
    const logoUrl = (await publicView.json()).logoUrl;
    expect(logoUrl ?? null, "a freshly seeded business has no logo").toBeNull();

    await page.goto(`/book/${seeded.slug}`);
    await expect(page.getByText(new RegExp(seeded.businessName.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"))))
      .toBeVisible({ timeout: SETTLE });

    await expect(
      booklyMark(page),
      "4.12: with no logo of its own the page must show the Bookly mark — a named, visible mark, " +
        "not an empty space the reader has to interpret as deliberate",
    ).toBeVisible({ timeout: SETTLE });
    expect(
      await brokenImages(page),
      "and it must not be an <img> with nothing behind it, which is the gap wearing a border",
    ).toEqual([]);
  });

  /**
   * 4.14 — a logo that fails to load falls back to the Bookly mark rather than a broken image.
   *
   * <p>The URL is well-formed https on a host that cannot resolve, which is the case that actually
   * happens: section 3 accepts that the image depends on someone else's hosting, and someone
   * else's hosting goes away. A malformed URL would be refused by the API under 4.13 and would
   * never reach a browser, so it tests nothing about the fallback.
   */
  test("aBrokenLogoFallsBackRatherThanBreaking", async ({ page, request }) => {
    const owner = await newOwner(request);
    const seeded = await seedBookable(request, owner, { durationMinutes: 30 });

    // The premise, established rather than assumed: this page really does render an owner's logo.
    // Without it a page that ignored logoUrl altogether would satisfy every assertion below, and
    // the criterion would be decided by a feature that was never built.
    //
    // The image is answered by the browser rather than fetched, because a test that depended on
    // somebody's CDN being up would fail for reasons that have nothing to do with Bookly. The
    // interception supplies an image that exists; it does not stand in for any behaviour under
    // test, which is entirely what the page does with the URL it was given.
    const working = "https://cdn.logo-fixture.example/brand.png";
    await page.route(working, (route) =>
      route.fulfill({
        status: 200,
        contentType: "image/png",
        body: Buffer.from(ONE_PIXEL_PNG, "base64"),
      }),
    );
    try {
      await setLogo(request, owner, seeded.businessId, working);
      await page.goto(`/book/${seeded.slug}`);
      await expect
        .poll(() => page.evaluate(() =>
          [...document.querySelectorAll("img")].some(
            (img) =>
              decodeURIComponent(img.currentSrc || img.src).includes("brand.png") &&
              img.complete &&
              img.naturalWidth > 0,
          ),
        ), {
          message:
            "a logo the owner set, on a host that answers, must actually appear on the booking " +
            "page — otherwise there is nothing for the next half of this test to break",
          timeout: SETTLE,
        })
        .toBe(true);
    } finally {
      await page.unrouteAll({ behavior: "ignoreErrors" });
    }

    // .invalid is reserved by RFC 2606 precisely so that it can never resolve.
    const dead = `https://logo-host-${Date.now()}.invalid/brand.png`;
    const stored = await setLogo(request, owner, seeded.businessId, dead);
    expect(
      stored.logoUrl,
      "an absolute https URL is accepted and stored, so what the page receives is a logo it has " +
        "every reason to believe in — it just happens to be on a host that has gone away",
    ).toBe(dead);

    await page.goto(`/book/${seeded.slug}`);
    await expect(page.getByText(new RegExp(seeded.businessName.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"))))
      .toBeVisible({ timeout: SETTLE });

    await expect(
      booklyMark(page),
      "4.14: when the logo cannot be fetched the page must fall back to the Bookly mark. The " +
        "customer is being asked to trust this address, and a broken-image icon is the single " +
        "strongest signal that they should not",
    ).toBeVisible({ timeout: 30_000 });
    await expect
      .poll(() => brokenImages(page), {
        message:
          "no broken <img> may remain on the page: the browser's own placeholder is exactly the " +
          "thing this criterion forbids, and leaving it beside the fallback shows both",
        timeout: 30_000,
      })
      .toEqual([]);
    await expect
      .poll(() => page.evaluate((url) =>
        [...document.querySelectorAll("img")].filter(
          (img) =>
            (img.currentSrc || img.src).includes(url) &&
            img.getBoundingClientRect().width > 0 &&
            img.getBoundingClientRect().height > 0,
        ).length,
        new URL(dead).host,
      ), {
        message:
          "and the image that could not load must be out of the layout, not merely invisible " +
          "beside the mark: two logos where there should be one is the gap with extra steps",
        timeout: 30_000,
      })
      .toBe(0);
  });
});
