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
