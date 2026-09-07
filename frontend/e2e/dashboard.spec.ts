import { test, expect, type APIRequestContext, type Page } from "@playwright/test";
import {
  bookViaPublicApi,
  localTimeIn,
  newOwner as newBookingOwner,
  ownersAppointments,
  publicAvailability,
  seedBookable,
  signIn as signInAsBookingOwner,
  upcoming,
  type PublicSlot,
} from "./support/fixtures";

/**
 * Turn-1 criterion 1.17: the frontend redirects an unauthenticated visit to /dashboard to /login.
 *
 * Written from the criterion and from ordinary observable behaviour; the author has not read
 * anything under frontend/src. The one contract taken as given is where the browser keeps its
 * session: localStorage under "bookly.tokens", holding accessToken, refreshToken and
 * expiresInSeconds.
 *
 * No backend runs for these tests, which is why they stay on the question the criterion actually
 * asks — what the browser does with client-side session state before it has spoken to anyone.
 *
 * The app shows a brief "Loading…" state while that state resolves, so every assertion here is
 * about the *settled* URL. Reading location immediately after navigating would be reading the
 * answer before the question had been decided.
 */

const SESSION_KEY = "bookly.tokens";

/** Playwright's default expect timeout is short; the first hit on a cold server is not. */
const SETTLE = 15_000;

/**
 * How long to wait while asserting a redirect does *not* happen. It has to outlast the app's own
 * loading state, because the defect this guards against fired late — after storage was read.
 */
const NO_REDIRECT_WINDOW = 6_000;

const LOGIN = /\/login(\/|\?|#|$)/;

async function seedSession(page: Page, session: unknown) {
  await page.addInitScript(
    ([key, value]) => {
      window.localStorage.setItem(key as string, value as string);
    },
    [SESSION_KEY, JSON.stringify(session)] as const,
  );
}

test.describe("dashboard access", () => {
  test("redirects an unauthenticated visit to /dashboard to /login", async ({ page }) => {
    await page.goto("/dashboard");

    await expect(
      page,
      "an unauthenticated visitor must not settle on /dashboard",
    ).toHaveURL(LOGIN, { timeout: SETTLE });
  });

  /**
   * A session the server no longer accepts is cleared, and the visitor is sent to the login form.
   *
   * <p>This used to be written with a synthetic token and the opposite expectation, and it was
   * wrong: since refresh-on-401 was wired, the app sends such a token to /api/auth/refresh, the
   * refresh fails because the server never issued it, and the app correctly concludes the session
   * is dead. Clearing it is right — the alternative is a visitor parked on a dashboard that can
   * never load, with no route to the login form that would fix it. The regression the old test was
   * guarding now lives in "keeps a visitor with a real session on /dashboard", which uses a session
   * the server actually issued so that a bounce can only mean the redirect fired too early.
   */
  test("a stored session the server rejects sends the visitor to login", async ({ page }) => {
    await seedSession(page, {
      accessToken: "not.a.token-the-server-ever-issued", // allow-secret: browser test fixture
      refreshToken: "11111111-2222-3333-4444-555555555555", // allow-secret: browser test fixture
      expiresInSeconds: 3600,
    });

    await page.goto("/dashboard");

    await expect(
      page,
      "a session that cannot be refreshed is dead, and a dead session belongs at the login form",
    ).toHaveURL(LOGIN, { timeout: SETTLE });
  });

  /**
   * Being bounced is only half of the criterion's promise. A visitor who is sent to /login and
   * finds a blank page has been redirected correctly and helped not at all.
   */
  test("the bounced visitor reaches a login form that renders", async ({ page }) => {
    await page.goto("/dashboard");
    await expect(page).toHaveURL(LOGIN, { timeout: SETTLE });

    const password = page.locator('input[type="password"]');
    const email = page.locator(
      'input[type="email"], input[name="email"], input[id="email"]',
    );
    const submit = page.locator(
      'button[type="submit"], input[type="submit"]',
    );

    await expect(password.first(), "a password field").toBeVisible({ timeout: SETTLE });
    await expect(email.first(), "an email field").toBeVisible({ timeout: SETTLE });
    await expect(submit.first(), "a control to submit the form").toBeVisible({
      timeout: SETTLE,
    });
  });

  /**
   * Beyond the letter of 1.17, and reported separately for that reason: stored state that is not a
   * usable session is not a session. An entry holding no access token cannot authenticate
   * anything, so treating its mere presence as "signed in" would leave the visitor on a dashboard
   * that can never load, instead of at the login form that would fix it.
   */
  test("stored state with no access token is not treated as a session", async ({ page }) => {
    await seedSession(page, { refreshToken: "", expiresInSeconds: 0 }); // allow-secret: browser test fixture

    await page.goto("/dashboard");

    await expect(
      page,
      "an entry under bookly.tokens carrying no access token must not count as being signed in",
    ).toHaveURL(LOGIN, { timeout: SETTLE });
  });
});

/* ------------------------------------------------------------------------- *
 * Turn 2: the dashboard screens. Criteria 2.20, 2.21 and 2.22.
 *
 * These need the backend, unlike the criterion-1.17 tests above. Each test seeds
 * its own business through the API and signs the browser in with the tokens that
 * seeding returned, so no test depends on a shared fixture or on the order the
 * suite happens to run in.
 *
 * The browser and the business are both pinned to UTC. What the screen renders is
 * an instant formatted in the viewer's zone, so leaving either to the machine's
 * settings would make the expected times depend on where the suite is run.
 *
 * Written from the criteria and from what a user can see; frontend/src was not read.
 * ------------------------------------------------------------------------- */

const API = process.env.E2E_API_URL ?? "http://localhost:8080";

const PASSWORD = "correct-horse-battery-staple-1"; // allow-secret: browser test fixture

/** Local times the engine offers, in the two formats a browser commonly renders. */
function timeIsShown(pageText: string, hhmm: string): boolean {
  const [h, m] = hhmm.split(":").map(Number);
  const twelve = `${((h + 11) % 12) + 1}:${String(m).padStart(2, "0")}`;
  const suffix = h < 12 ? "AM" : "PM";
  return (
    new RegExp(`\\b${hhmm}\\b`).test(pageText) ||
    new RegExp(`\\b${twelve}\\s*${suffix}\\b`, "i").test(pageText)
  );
}

type Owner = { tokens: unknown; auth: Record<string, string> };

type Seeded = {
  businessId: string;
  businessName: string;
  serviceName: string;
  serviceId: string;
  employeeName: string;
  owner: Owner;
};

async function newOwner(request: APIRequestContext): Promise<Owner> {
  const email = `e2e-${Date.now()}-${Math.floor(Math.random() * 1e6)}@example.test`;
  const registered = await request.post(`${API}/api/auth/register`, {
    data: { email, password: PASSWORD, fullName: "Dashboard Owner" },
  });
  expect(registered.status(), "seeding: register").toBe(201);
  const loggedIn = await request.post(`${API}/api/auth/login`, { data: { email, password: PASSWORD } });
  expect(loggedIn.status(), "seeding: login").toBe(200);
  const tokens = await loggedIn.json();
  return { tokens, auth: { Authorization: `Bearer ${tokens.accessToken}` } };
}

/** Creates one business, configured as far as the options ask for. */
async function seed(
  request: APIRequestContext,
  owner: Owner,
  options: { withService?: boolean; withEmployee?: boolean; withHours?: boolean },
): Promise<Seeded> {
  const stamp = `${Date.now()}-${Math.floor(Math.random() * 1e6)}`;
  const businessName = `E2E Salon ${stamp}`;
  const business = await request.post(`${API}/api/businesses`, {
    data: { name: businessName, timezone: "UTC" },
    headers: owner.auth,
  });
  expect(business.status(), "seeding: create business").toBe(201);
  const businessId = (await business.json()).id;

  const serviceName = `Signature Cut ${stamp}`;
  const employeeName = `Alex Stylist ${stamp}`;
  let serviceId = "";
  if (options.withService) {
    const service = await request.post(`${API}/api/businesses/${businessId}/services`, {
      data: { name: serviceName, durationMinutes: 60, priceMinor: 5000 },
      headers: owner.auth,
    });
    expect(service.status(), "seeding: create service").toBe(201);
    serviceId = (await service.json()).id;
  }
  if (options.withEmployee) {
    const employee = await request.post(`${API}/api/businesses/${businessId}/employees`, {
      data: { fullName: employeeName },
      headers: owner.auth,
    });
    expect(employee.status(), "seeding: create employee").toBe(201);
    const employeeId = (await employee.json()).id;
    if (serviceId) {
      await request.put(`${API}/api/businesses/${businessId}/employees/${employeeId}/services`, {
        data: { serviceIds: [serviceId] },
        headers: owner.auth,
      });
    }
    if (options.withHours) {
      // Every weekday, so the screen's own choice of date cannot decide the outcome.
      for (const weekday of ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"]) {
        const hours = await request.post(
          `${API}/api/businesses/${businessId}/employees/${employeeId}/working-hours`,
          { data: { weekday, startsAt: "09:00:00", endsAt: "12:00:00" }, headers: owner.auth },
        );
        expect(hours.status(), "seeding: working hours").toBe(201);
      }
    }
  }
  return { businessId, businessName, serviceName, serviceId, employeeName, owner };
}

async function signIn(page: Page, owner: Owner) {
  await page.addInitScript(
    ([key, value]) => window.localStorage.setItem(key as string, value as string),
    [SESSION_KEY, JSON.stringify(owner.tokens)] as const,
  );
}

/**
 * Picks a service in the availability screen's picker. The option's text is the service name with
 * its duration appended, so it is matched by substring and selected by value rather than by an
 * exact label the screen never promised.
 */
async function chooseService(page: Page, serviceName: string) {
  const picker = page.locator("select").first();
  const option = picker.locator("option", { hasText: serviceName });
  await expect(option, `the picker must offer "${serviceName}"`).toHaveCount(1, { timeout: SETTLE });
  await picker.selectOption((await option.getAttribute("value")) ?? "");
}

async function settledText(page: Page): Promise<string> {
  await expect(page.getByText(/loading/i)).toHaveCount(0, { timeout: SETTLE });
  return page.locator("body").innerText();
}

/** The day heading the calendar prints for an instant, in the given zone: "Wed 21 Oct". */
function dayLabelIn(instant: string, timeZone: string): string {
  return new Intl.DateTimeFormat("en-GB", {
    timeZone,
    weekday: "short",
    day: "numeric",
    month: "short",
  }).format(new Date(instant));
}

/**
 * Splits the calendar's text into what sits under each day heading.
 *
 * <p>Bucketing by the visible headings rather than by DOM structure keeps this test about what a
 * reader sees: whichever way the week is laid out, the appointment has to appear beneath the right
 * day's label.
 */
function dayBuckets(text: string): Record<string, string> {
  const heading = /\b(?:Mon|Tue|Wed|Thu|Fri|Sat|Sun)\s+\d{1,2}\s+[A-Za-z]+\b/g;
  const labels = [...text.matchAll(heading)];
  const buckets: Record<string, string> = {};
  labels.forEach((match, index) => {
    const from = (match.index ?? 0) + match[0].length;
    const to = index + 1 < labels.length ? labels[index + 1].index ?? text.length : text.length;
    buckets[match[0]] = text.slice(from, to);
  });
  return buckets;
}

const ERROR_TEXT = /could not|couldn't|went wrong|failed|try again/i;
const GUIDANCE_TEXT = /add|create|get started|first|no .* yet/i;

/** Holds the API open and looks while it is still open. */
async function assertLoadingStateIsShown(page: Page, url: string) {
  await page.route("**/api/**", async (route) => {
    await new Promise((resolve) => setTimeout(resolve, 1500));
    try {
      await route.continue();
    } catch {
      // The page moved on while this was held; there is nothing left to continue.
    }
  });
  try {
    await page.goto(url);
    await expect(
      page.getByText(/loading/i).first(),
      "a request that has not answered yet must say so",
    ).toBeVisible({ timeout: SETTLE });
  } finally {
    await page.unrouteAll({ behavior: "ignoreErrors" });
  }
}

/** Fails the matching API calls and asserts the screen says so, rather than waiting forever. */
async function assertErrorStateIsShown(
  page: Page,
  url: string,
  options: { failing?: string; prepare?: (page: Page) => Promise<void>; because?: string } = {},
): Promise<string> {
  const { failing = "**/api/**", prepare, because = "" } = options;
  await page.route(failing, (route) =>
    route.fulfill({
      status: 500,
      contentType: "application/json",
      body: JSON.stringify({ code: "BOOM", message: "boom" }),
    }),
  );
  try {
    await page.goto(url);
    if (prepare) {
      await prepare(page);
    }
    await expect(
      page.getByText(ERROR_TEXT).first(),
      `a failure must look like a failure${because}: a screen that keeps saying "loading" after ` +
        "the request has failed is showing one of its four states in place of another",
    ).toBeVisible({ timeout: SETTLE });
    return await page.locator("body").innerText();
  } finally {
    await page.unrouteAll({ behavior: "ignoreErrors" });
  }
}

test.describe("dashboard screens", () => {
  test.use({ timezoneId: "UTC" });

  test.beforeAll(async ({ request }) => {
    const health = await request.get(`${API}/actuator/health`).catch(() => null);
    expect(
      health?.ok(),
      `these tests need the backend at ${API}; start it before running the suite`,
    ).toBe(true);
  });

  /**
   * The defect the browser tests found, kept as its own regression rather than left implicit in
   * three failing screens. A browser sends a preflight before any cross-origin API call and sends
   * no credentials on it; a chain that answers 401 makes every dashboard fetch impossible.
   */
  test("the API accepts a cross-origin preflight from the dashboard's origin", async ({ request }) => {
    const preflight = await request.fetch(`${API}/api/businesses`, {
      method: "OPTIONS",
      headers: {
        Origin: "http://localhost:3000",
        "Access-Control-Request-Method": "GET",
        "Access-Control-Request-Headers": "authorization",
      },
    });

    expect(
      preflight.status(),
      "a CORS preflight carries no credentials by construction, so answering it 401 blocks " +
        "every call the dashboard makes",
    ).toBeLessThan(400);
    expect(
      preflight.headers()["access-control-allow-origin"],
      "the preflight must allow the origin the frontend is served from",
    ).toBeTruthy();
  });

  /**
   * 2.20 for the three list screens. CLAUDE.md's rule is every screen that loads data, and the
   * overview loads data too, so it is covered in its own test below alongside availability, which
   * needs a service chosen before it has anything to show.
   */
  for (const screen of ["overview", "services", "employees"] as const) {
    test(`2.20 the ${screen} screen renders loading, empty, error and content distinguishably`, async ({
      page,
      request,
    }) => {
      const owner = await newOwner(request);
      const bare = await seed(request, owner, {});
      const configured = await seed(request, owner, { withService: true, withEmployee: true });
      const suffix = screen === "overview" ? "" : `/${screen}`;
      const url = (id: string) => `/dashboard/${id}${suffix}`;
      await signIn(page, owner);

      await assertLoadingStateIsShown(page, url(bare.businessId));

      await page.goto(url(bare.businessId));
      const emptyText = await settledText(page);
      expect(emptyText, `the ${screen} screen of a bare business must not report a failure`)
        .not.toMatch(ERROR_TEXT);

      const errorText = await assertErrorStateIsShown(page, url(bare.businessId));

      await page.goto(url(configured.businessId));
      const contentText = await settledText(page);
      const marker =
        screen === "overview"
          ? /\d+\s+service/i
          : new RegExp(screen === "services" ? configured.serviceName : configured.employeeName);
      expect(contentText, `the ${screen} screen must show what the business has`).toMatch(marker);

      expect(emptyText, "empty must not read as content").not.toMatch(marker);
      expect(emptyText, "empty must not read as error").not.toEqual(errorText);
      expect(contentText, "content must not read as error").not.toEqual(errorText);
    });
  }

  /**
   * 2.20 for the availability screen. Its error state is asserted twice on purpose: once when the
   * availability request itself fails, and once when the service list it depends on fails. A screen
   * that reports the first and hangs on the second has an error state only for the failure its
   * author happened to think of.
   */
  test("2.20 the availability screen renders loading, empty, error and content distinguishably", async ({
    page,
    request,
  }) => {
    const owner = await newOwner(request);
    const bookable = await seed(request, owner, { withService: true, withEmployee: true, withHours: true });
    const noHours = await seed(request, owner, { withService: true, withEmployee: true });
    const url = (id: string) => `/dashboard/${id}/availability`;
    const date = upcoming("THURSDAY");
    await signIn(page, owner);

    await assertLoadingStateIsShown(page, url(bookable.businessId));

    // Empty: a service nobody has hours for. A real question with an empty answer.
    await page.goto(url(noHours.businessId));
    await chooseService(page, noHours.serviceName);
    await page.locator("input[type=date]").fill(date);
    const emptyText = await settledText(page);

    // Error, when the availability request itself fails. The service list still loads, so the
    // screen has everything it needs to report the failure.
    const errorText = await assertErrorStateIsShown(page, url(bookable.businessId), {
      // Scoped to the API: the screen's own URL ends in /availability too, and a glob that
      // matched it would replace the page itself rather than the request it makes.
      failing: "**/api/**availability**",
      because: " when the availability request fails",
      prepare: async (p) => {
        await chooseService(p, bookable.serviceName);
        await p.locator("input[type=date]").fill(date);
      },
    });

    // Error, when the list of services the screen depends on fails. The screen cannot offer a
    // choice it could not load, and saying nothing leaves the reader waiting on a request that
    // already failed.
    await assertErrorStateIsShown(page, url(bookable.businessId), {
      because: " when the service list fails",
    });

    // Content.
    await page.goto(url(bookable.businessId));
    await chooseService(page, bookable.serviceName);
    await page.locator("input[type=date]").fill(date);
    const contentText = await settledText(page);
    expect(contentText, "the availability screen must show the times it computed").toMatch(/\d{1,2}:\d{2}/);

    expect(emptyText, "empty must not read as error").not.toMatch(ERROR_TEXT);
    expect(emptyText, "empty must not read as content").not.toEqual(contentText);
    expect(contentText, "content must not read as error").not.toEqual(errorText);
  });

  /** 2.21 — a business with nothing yet is told what to do next. */
  test("newBusinessIsGuided", async ({ page, request }) => {
    const owner = await newOwner(request);
    const fresh = await seed(request, owner, {});
    await signIn(page, owner);

    for (const suffix of ["", "/services", "/employees"]) {
      const screen = suffix === "" ? "overview" : suffix.slice(1);
      await page.goto(`/dashboard/${fresh.businessId}${suffix}`);
      const text = await settledText(page);

      // The add form is on the screen in every state, including the error state, so matching
      // "Add a service" alone would pass while the screen was actually apologising. The empty
      // state has to be the empty state first.
      expect(text, `the ${screen} screen must be showing its empty state, not its error state`)
        .not.toMatch(ERROR_TEXT);
      expect(
        text,
        `the ${screen} screen of a new business must say what to do next, not present an empty table`,
      ).toMatch(GUIDANCE_TEXT);
      await expect(
        page.locator("button[type=submit], a[href]").filter({ hasText: /add|create|new|check/i }).first(),
        `the ${screen} screen must offer the action it is recommending`,
      ).toBeVisible({ timeout: SETTLE });
      await expect(
        page.locator("table tbody tr"),
        "an empty table is the thing this criterion exists to forbid",
      ).toHaveCount(0);
    }
  });

  /** 2.22 — the availability view shows the engine's real slots, or says plainly there are none. */
  test("availabilityShowsRealSlotsOrSaysThereAreNone", async ({ page, request }) => {
    const owner = await newOwner(request);
    const bookable = await seed(request, owner, { withService: true, withEmployee: true, withHours: true });
    await signIn(page, owner);

    // What the engine actually answers for the date the screen will be asked about.
    const date = upcoming("THURSDAY");
    const api = await request.get(
      `${API}/api/businesses/${bookable.businessId}/availability` +
        `?serviceId=${bookable.serviceId}&date=${date}`,
      { headers: owner.auth },
    );
    expect(api.status(), "the engine answers over the API").toBe(200);
    const expectedSlots: { start: string }[] = (await api.json()).slots;
    expect(expectedSlots.length, "the seeded 09:00-12:00 window offers slots").toBeGreaterThan(0);
    const expectedTimes = expectedSlots.map((s) => s.start.substring(11, 16));

    await page.goto(`/dashboard/${bookable.businessId}/availability`);
    await chooseService(page, bookable.serviceName);
    await page.locator("input[type=date]").fill(date);
    const shown = await settledText(page);

    for (const time of [expectedTimes[0], expectedTimes[expectedTimes.length - 1]]) {
      expect(timeIsShown(shown, time), `the view must show the engine's slot at ${time}`).toBe(true);
    }
    for (const notOffered of ["08:45", "12:15"]) {
      expect(
        timeIsShown(shown, notOffered),
        `${notOffered} is outside the working window and must not be offered`,
      ).toBe(false);
    }

    // And a business whose employee has no hours has nothing to offer, said plainly.
    const unbookable = await seed(request, owner, { withService: true, withEmployee: true });
    await page.goto(`/dashboard/${unbookable.businessId}/availability`);
    await chooseService(page, unbookable.serviceName);
    await page.locator("input[type=date]").fill(date);
    const none = await settledText(page);

    expect(
      none,
      "no availability must be stated, not left as a blank area the reader has to interpret",
    ).toMatch(/no (available |free )?(slots|times|availability)|nothing available|none available|no times/i);
  });

  /**
   * 1.17's other half, which was left untested when the synthetic-session fixture it used became
   * stale: a visitor who *is* signed in must not be bounced. The session here is one the server
   * actually issued, so a redirect to /login can only mean the page decided before it had read
   * storage — which is the defect this guards, and the reason the original test existed.
   */
  test("keeps a visitor with a real session on /dashboard", async ({ page, request }) => {
    const owner = await newOwner(request);
    const seeded = await seed(request, owner, {});
    await signIn(page, owner);

    await page.goto(`/dashboard/${seeded.businessId}`);

    const bounced = await page
      .waitForURL(LOGIN, { timeout: NO_REDIRECT_WINDOW })
      .then(() => true)
      .catch(() => false);
    expect(
      bounced,
      "a visitor holding a session the server issued was sent to the login form; a redirect " +
        "decided before stored session state has been read bounces signed-in users on every reload",
    ).toBe(false);
    await expect(page).toHaveURL(/\/dashboard/, { timeout: SETTLE });
  });

  /** 3.21 — the owner sees a booking made on the public page, and can cancel it. */
  test("theOwnerSeesAndCancelsABooking", async ({ page, request }) => {
    const owner = await newBookingOwner(request);
    const seeded = await seedBookable(request, owner, { durationMinutes: 30 });
    const date = upcoming("THURSDAY");

    // A visitor books on the public page — the same route a browser would have used.
    const availability = await publicAvailability(request, seeded.slug, seeded.serviceId, date);
    expect(availability.slots.length, "the seeded day offers times").toBeGreaterThan(0);
    const booked = availability.slots[0].start;
    const bookedLabel = localTimeIn(booked, availability.timezone);
    const taken = await bookViaPublicApi(request, seeded.slug, {
      serviceId: seeded.serviceId,
      employeeId: seeded.employeeId,
      startsAt: booked,
      name: "Priya Visitor",
    });
    expect(taken.status(), "the public booking").toBe(201);

    await signInAsBookingOwner(page, owner);
    await page.goto(`/dashboard/${seeded.businessId}/appointments`);
    const dateInputs = page.locator("input[type=date]");
    await dateInputs.nth(0).fill(date);
    await dateInputs.nth(1).fill(date);
    await expect(page.getByText(/priya visitor/i), "the owner sees who booked")
      .toBeVisible({ timeout: SETTLE });

    const before = await page.locator("body").innerText();
    expect(before, "and what they booked").toContain(seeded.serviceName);
    expect(before, "and with whom").toContain(seeded.employeeName);
    expect(
      before,
      `and when, on the business's clock (${bookedLabel} in ${seeded.timezone}) rather than the ` +
        "viewer's",
    ).toContain(bookedLabel);
    expect(before, "a live booking reads as live").toMatch(/confirmed|pending/i);

    await page.getByRole("button", { name: /^cancel$/i }).first().click();

    await expect
      .poll(
        async () => {
          const list = await ownersAppointments(request, owner, seeded.businessId, date, date);
          return list[0]?.status;
        },
        {
          message:
            "cancelling in the dashboard must actually cancel the appointment — the owner is the " +
            "one person who has to be able to undo a booking, and a button that only looks like " +
            "it worked leaves a customer expected who is not coming",
          timeout: SETTLE,
        },
      )
      .toBe("CANCELLED");

    const after = await page.locator("body").innerText();
    expect(after, "the screen must not still present it as a live booking").not.toMatch(/confirmed|pending/i);
    expect(after, "and the page must visibly change, not silently succeed").not.toEqual(before);
  });

  /**
   * 3.22 — the calendar shows a week, places each appointment in the right day and time in the
   * business's zone, and shows an empty week as seven labelled days.
   *
   * <p>Staged to catch the one bug most likely to be there: an appointment at midnight in Auckland
   * is the previous afternoon in Los Angeles, which is where this browser's clock is set. A
   * calendar that bucketed by the viewer's clock would put it on the wrong day *and* at the wrong
   * time, and would look perfectly correct to anyone testing from Auckland.
   */
  test("theCalendarPlacesAppointmentsCorrectly", async ({ page, request }) => {
    const owner = await newBookingOwner(request);
    const seeded = await seedBookable(request, owner, {
      durationMinutes: 30,
      timezone: "Pacific/Auckland",
      // Midnight on Wednesday, ordinary hours on Thursday.
      hours: [
        { weekday: "WEDNESDAY", start: "00:00:00", end: "01:00:00" },
        { weekday: "THURSDAY", start: "09:00:00", end: "17:00:00" },
      ],
    });
    const wednesday = upcoming("WEDNESDAY");

    // The first slot of a 00:00-01:00 window is midnight itself. Taken from the engine rather than
    // computed here, so the zone offset on the day is the tzdb's answer and not mine.
    const availability = await publicAvailability(request, seeded.slug, seeded.serviceId, wednesday);
    expect(availability.slots.length, "the midnight window offers times").toBeGreaterThan(0);
    const midnight = availability.slots[0].start;
    expect(
      localTimeIn(midnight, seeded.timezone),
      "the fixture is only interesting if the appointment really is at midnight there",
    ).toBe("00:00");
    const businessDayLabel = dayLabelIn(midnight, seeded.timezone);
    const viewerDayLabel = dayLabelIn(midnight, "America/Los_Angeles");
    expect(
      viewerDayLabel,
      "the stage only tests anything if the two clocks disagree about the day",
    ).not.toBe(businessDayLabel);

    const booked = await bookViaPublicApi(request, seeded.slug, {
      serviceId: seeded.serviceId,
      employeeId: seeded.employeeId,
      startsAt: midnight,
      name: "Midnight Customer",
    });
    expect(booked.status(), "the midnight booking").toBe(201);

    await signInAsBookingOwner(page, owner);
    await page.goto(`/dashboard/${seeded.businessId}/calendar`);
    await expect(page.getByText(/times shown in/i)).toBeVisible({ timeout: SETTLE });

    // Walk forward to the week that holds it. Bounded, and it asserts it arrived.
    let reached = false;
    for (let week = 0; week < 12 && !reached; week++) {
      if ((await page.locator("body").innerText()).includes(businessDayLabel)) {
        reached = true;
        break;
      }
      await page.getByRole("button", { name: /next week/i }).click();
      await page.waitForTimeout(400);
    }
    expect(reached, `the calendar must be able to show the week containing ${businessDayLabel}`).toBe(true);

    const days = dayBuckets(await page.locator("body").innerText());
    expect(
      Object.keys(days).length,
      "a week is seven days, labelled, whatever is in them: %s" + JSON.stringify(Object.keys(days)),
    ).toBe(7);
    expect(
      days[businessDayLabel],
      `the appointment belongs under ${businessDayLabel} — the day it is on the business's clock`,
    ).toContain("Midnight Customer");
    expect(
      days[businessDayLabel],
      `and at 00:00, the time the customer chose, not ${localTimeIn(midnight, "America/Los_Angeles")} ` +
        "which is what the same instant reads as in this browser's zone",
    ).toContain("00:00");
    if (days[viewerDayLabel] !== undefined) {
      expect(
        days[viewerDayLabel],
        `${viewerDayLabel} is where a calendar bucketing by the viewer's clock would have put it`,
      ).not.toContain("Midnight Customer");
    }

    // An empty week is still a week.
    await page.getByRole("button", { name: /next week/i }).click();
    await expect(page.getByText(/no appointments this week|nothing booked/i))
      .toBeVisible({ timeout: SETTLE });
    const emptyWeek = dayBuckets(await page.locator("body").innerText());
    expect(
      Object.keys(emptyWeek).length,
      "an empty week must still show seven labelled days: a blank page cannot be told apart from " +
        "a page that failed to load",
    ).toBe(7);
    expect(
      Object.values(emptyWeek).join(""),
      "and it must say it is empty rather than leaving the reader to infer it",
    ).not.toContain("Midnight Customer");
  });
});

/* ---------------------------------------------------------------------------
 * Turn 4, criteria 4.5 to 4.9 — booking alerts on the owner's dashboard.
 *
 * Written from `docs/spec/turn-4.md`; frontend/src was not read.
 *
 * Every alert here is raised the way a real one is: a stranger books through the public API while
 * the owner's browser sits on the dashboard, and the page finds out by polling. Nothing is
 * injected and no response is stubbed, because the thing under test is precisely whether the page
 * notices a change it was not told about.
 * ------------------------------------------------------------------------- */

/** How long to allow for a poll to happen and be noticed. The interval is about 20 seconds. */
const POLL_WINDOW = 75_000;

/**
 * What counts as an alert.
 *
 * <p>A toast is a transient announcement, so it must be exposed as one: `role="status"` or
 * `role="alert"` is how assistive technology is told that something appeared without the reader
 * having moved. An owner who cannot see the screen is still running the business. The testid is
 * accepted as an alternative so this does not hinge on which of the two the page chose, and every
 * sample is filtered by the text of the booking under test, so a loading indicator that also
 * carries `role="status"` cannot be mistaken for an alert.
 */
const ALERT_SELECTOR = '[role="alert"], [role="status"], [data-testid="toast"]';

async function visibleAlertTexts(page: Page): Promise<string[]> {
  return page
    .evaluate((selector) => {
      return [...document.querySelectorAll(selector)]
        .filter((el) => {
          const box = el.getBoundingClientRect();
          const style = getComputedStyle(el);
          return (
            box.width > 0 &&
            box.height > 0 &&
            style.visibility !== "hidden" &&
            style.display !== "none" &&
            style.opacity !== "0"
          );
        })
        .map((el) => (el.textContent ?? "").replace(/\s+/g, " ").trim())
        .filter((text) => text.length > 0);
    }, ALERT_SELECTOR)
    .catch(() => [] as string[]);
}

type AlertWatch = {
  /** The text of each *appearance*: one entry per time an alert about this booking came back. */
  appearances: string[];
  /** The largest number of matching alerts on screen at once. */
  peak: number;
  stop: () => Promise<void>;
};

/**
 * Counts how many times an alert about one booking appears, over as long as it is watched.
 *
 * <p>Counting appearances rather than reading the screen at the end is what makes 4.7 testable at
 * all: 4.8 requires a toast to expire, so by the time the second poll has happened the first toast
 * is legitimately gone, and "is there a toast now" cannot tell "alerted once, correctly" from
 * "alerted twice and the second one also expired". A transition from nothing-matching to
 * something-matching is exactly one alert.
 */
function watchAlerts(page: Page, matches: (text: string) => boolean): AlertWatch {
  const watch: AlertWatch = { appearances: [], peak: 0, stop: async () => {} };
  let present = false;
  let stopped = false;
  // A page that closes ends the watch, so a failing test cannot leave this loop running.
  page.on("close", () => {
    stopped = true;
  });
  const loop = (async () => {
    while (!stopped) {
      const matching = (await visibleAlertTexts(page)).filter(matches);
      watch.peak = Math.max(watch.peak, matching.length);
      if (matching.length > 0 && !present) watch.appearances.push(matching.join(" | "));
      present = matching.length > 0;
      await new Promise((resolve) => setTimeout(resolve, 300));
    }
  })();
  watch.stop = async () => {
    stopped = true;
    await loop;
  };
  return watch;
}

/**
 * Counts poll *cycles*, so a test can wait for polling to happen rather than sleep for a guess.
 *
 * <p>Counting requests would not do it: opening the dashboard fetches the business, its services,
 * its people and its appointments in one go, so "four requests have happened" is true before the
 * page has finished loading and says nothing about whether anything was ever re-read. What a cycle
 * is, is the *same* resource fetched again — so this returns how many times the most-refetched
 * path under this business has been read, which is one on arrival and two after the first poll.
 * The query string is dropped so that a poll carrying a moving date range still counts as the same
 * resource.
 */
function countPolls(page: Page, businessId: string): () => number {
  const readsByPath = new Map<string, number>();
  page.on("response", (response) => {
    if (response.request().method() !== "GET" || response.status() !== 200) return;
    if (!response.url().includes(`/api/businesses/${businessId}`)) return;
    const path = new URL(response.url()).pathname;
    readsByPath.set(path, (readsByPath.get(path) ?? 0) + 1);
  });
  return () => Math.max(0, ...readsByPath.values());
}

/**
 * `count` of the day's offered times that do not overlap one another.
 *
 * <p>Consecutive slots are a step apart, not a service apart, so `slots[0]` and `slots[1]` are
 * usually the same half hour offered twice. Booking both is a 409 from the overlap constraint —
 * correct behaviour, and nothing at all to do with the alert under test.
 */
function spacedSlots(slots: PublicSlot[], count: number): PublicSlot[] {
  const chosen: PublicSlot[] = [];
  let freeFrom = 0;
  for (const slot of slots) {
    if (Date.parse(slot.start) >= freeFrom) {
      chosen.push(slot);
      freeFrom = Date.parse(slot.end);
    }
    if (chosen.length === count) break;
  }
  expect(chosen.length, `the seeded day must offer ${count} times that do not overlap`).toBe(count);
  return chosen;
}

/** Books through the public API as a stranger would, and returns what they booked. */
async function bookOutOfBand(
  request: APIRequestContext,
  seeded: { slug: string; serviceId: string; employeeId: string; timezone: string },
  startsAt: string,
  customerName: string,
) {
  const response = await bookViaPublicApi(request, seeded.slug, {
    serviceId: seeded.serviceId,
    employeeId: seeded.employeeId,
    startsAt,
    name: customerName,
  });
  expect(response.status(), `the out-of-band booking for ${customerName}`).toBe(201);
  return response.json();
}

test.describe("turn 4 — booking alerts", () => {
  // The business is in Auckland and the owner is watching from Los Angeles, so "the time" in a
  // toast has exactly one correct answer and it is not the one the viewer's clock gives.
  test.use({ timezoneId: "America/Los_Angeles" });

  test.beforeAll(async ({ request }) => {
    const health = await request.get(`${API}/actuator/health`).catch(() => null);
    expect(health?.ok(), `these tests need the backend at ${API}`).toBe(true);
  });

  /**
   * 4.5 — bookings already present when the dashboard opens raise no alert.
   *
   * <p>Pitfall 3: every booking looks new to an empty set. An owner opening the dashboard on a
   * busy Thursday must not be met by a wall of toasts for appointments they made last week, and
   * the toast that cried wolf is the one they will ignore when it is real.
   */
  test("existingBookingsDoNotAlert", async ({ page, request }) => {
    test.setTimeout(180_000);
    const owner = await newBookingOwner(request);
    const seeded = await seedBookable(request, owner, { durationMinutes: 30 });
    const date = upcoming("THURSDAY");

    const availability = await publicAvailability(request, seeded.slug, seeded.serviceId, date);
    expect(availability.slots.length, "the seeded day offers times").toBeGreaterThan(0);
    const already = ["Prior Customer Ada", "Prior Customer Bea", "Prior Customer Cal"];
    const priorSlots = spacedSlots(availability.slots, already.length);
    for (let i = 0; i < already.length; i++) {
      await bookOutOfBand(request, seeded, priorSlots[i].start, already[i]);
    }

    await signInAsBookingOwner(page, owner);
    const polls = countPolls(page, seeded.businessId);
    const watch = watchAlerts(page, (text) => already.some((name) => text.includes(name)));
    await page.goto(`/dashboard/${seeded.businessId}`);
    await settledText(page);

    // Wait for real polls rather than a fixed sleep: the criterion is about what the second and
    // third read of an unchanged list do, so the test has to know they happened.
    await expect
      .poll(polls, {
        message: "the dashboard must actually be polling, or this test proves nothing about it",
        timeout: POLL_WINDOW,
      })
      .toBeGreaterThanOrEqual(3);
    await watch.stop();

    expect(
      watch.appearances,
      "4.5 and pitfall 3: bookings that were already there when the dashboard opened must raise " +
        "nothing. The first successful response seeds what is known; it is not a set of changes",
    ).toEqual([]);
  });

  /**
   * 4.6 and 4.7 — a booking made while the dashboard is open raises exactly one toast, naming the
   * customer, the service and the time, and never alerts again however many polls follow.
   */
  test("aNewBookingRaisesOneToast", async ({ page, request }) => {
    test.setTimeout(240_000);
    const owner = await newBookingOwner(request);
    const seeded = await seedBookable(request, owner, { durationMinutes: 30 });
    const date = upcoming("THURSDAY");

    const availability = await publicAvailability(request, seeded.slug, seeded.serviceId, date);
    expect(availability.slots.length, "the seeded day offers times").toBeGreaterThan(0);
    const [existing, arriving] = spacedSlots(availability.slots, 2);
    // One booking already on the books, so the new one has to be told apart from an existing one
    // rather than merely from nothing.
    await bookOutOfBand(request, seeded, existing.start, "Prior Customer Dee");

    await signInAsBookingOwner(page, owner);
    const polls = countPolls(page, seeded.businessId);
    const customer = `Nadia Newcomer ${Date.now()}`;
    const watch = watchAlerts(page, (text) => text.includes(customer));
    await page.goto(`/dashboard/${seeded.businessId}`);
    await settledText(page);

    // The dashboard must have read the list at least once before the new booking exists, or
    // "already present" and "arrived while open" are the same thing.
    await expect
      .poll(polls, { message: "the dashboard reads the business before anything changes", timeout: POLL_WINDOW })
      .toBeGreaterThanOrEqual(1);

    const startsAt = arriving.start;
    await bookOutOfBand(request, seeded, startsAt, customer);
    const expectedTime = localTimeIn(startsAt, seeded.timezone);

    await expect
      .poll(() => watch.appearances.length, {
        message:
          "4.6: a booking made while the dashboard is open must raise a toast. An owner who only " +
          "learns of a booking by reloading is still doing the polling themselves",
        timeout: POLL_WINDOW,
      })
      .toBeGreaterThanOrEqual(1);

    const toast = watch.appearances[0];
    expect(toast, "4.6: the toast names the customer").toContain(customer);
    expect(toast, "and what they booked").toContain(seeded.serviceName);
    expect(
      timeIsShown(toast, expectedTime),
      `4.6: and when it is — ${expectedTime} on the business's clock (${seeded.timezone}). ` +
        "This browser is in America/Los_Angeles, and an alert that named the viewer's reading of " +
        `the instant would say ${localTimeIn(startsAt, "America/Los_Angeles")} instead. ` +
        `The toast said: ${toast}`,
    ).toBe(true);
    expect(
      watch.peak,
      "one booking is one alert: pitfall 4 forbids a wall of toasts, and two copies of the same " +
        "booking is where that wall starts",
    ).toBe(1);

    // 4.7 — hold the page open across two further poll cycles and see whether it says it again.
    const pollsAtAlert = polls();
    await expect
      .poll(polls, {
        message: "two more polls must actually occur, or 'never alerts twice' has not been tested",
        timeout: POLL_WINDOW * 2,
      })
      .toBeGreaterThanOrEqual(pollsAtAlert + 2);
    await watch.stop();

    expect(
      watch.appearances,
      "4.7: the same booking must never alert twice, however many polls occur. A page that " +
        "compares each response against the one before it, rather than against what it has " +
        "already told the owner about, re-announces the same appointment every twenty seconds " +
        "until the owner closes the tab",
    ).toHaveLength(1);
  });

  /**
   * 4.8 — a toast can be dismissed, and disappears on its own.
   *
   * <p>Pitfall 4: a notification that cannot be got rid of is worse than no notification. Both
   * halves are required — a toast that only expires cannot be cleared by an owner who wants it
   * gone now, and one that only dismisses stays on screen forever if nobody is looking.
   */
  test("aToastCanBeDismissedAndAlsoDisappearsOnItsOwn", async ({ page, request }) => {
    test.setTimeout(240_000);
    const owner = await newBookingOwner(request);
    const seeded = await seedBookable(request, owner, { durationMinutes: 30 });
    const date = upcoming("THURSDAY");

    const availability = await publicAvailability(request, seeded.slug, seeded.serviceId, date);
    const [forDismissal, forExpiry] = spacedSlots(availability.slots, 2);

    await signInAsBookingOwner(page, owner);
    const polls = countPolls(page, seeded.businessId);
    await page.goto(`/dashboard/${seeded.businessId}`);
    await settledText(page);
    await expect
      .poll(polls, { message: "the dashboard reads the business before anything changes", timeout: POLL_WINDOW })
      .toBeGreaterThanOrEqual(1);

    // --- dismissed by the owner.
    const dismissed = `Dismissed Customer ${Date.now()}`;
    await bookOutOfBand(request, seeded, forDismissal.start, dismissed);
    const firstToast = page.locator(ALERT_SELECTOR).filter({ hasText: dismissed }).first();
    await expect(firstToast, "the toast for the first new booking").toBeVisible({ timeout: POLL_WINDOW });

    const close = firstToast.getByRole("button").first();
    await expect(
      close,
      "4.8: a toast must offer a way to get rid of it. An interruption the reader cannot close is " +
        "an interruption they will learn to work around, which costs the feature its point",
    ).toBeVisible({ timeout: SETTLE });
    await close.click();
    await expect(
      firstToast,
      "and dismissing it must remove it promptly, not merely start its timer",
    ).toBeHidden({ timeout: 5_000 });

    // --- and expires by itself.
    const ignored = `Ignored Customer ${Date.now()}`;
    await bookOutOfBand(request, seeded, forExpiry.start, ignored);
    const secondToast = page.locator(ALERT_SELECTOR).filter({ hasText: ignored }).first();
    await expect(secondToast, "the toast for the second new booking").toBeVisible({ timeout: POLL_WINDOW });
    await expect(
      secondToast,
      "4.8: a toast nobody touches must expire. The owner is working, not watching, and toasts " +
        "that never leave stack into the wall pitfall 4 forbids",
    ).toBeHidden({ timeout: 60_000 });

    expect(
      await visibleAlertTexts(page).then((texts) => texts.filter((t) => t.includes(ignored) || t.includes(dismissed))),
      "and neither of them is left behind on screen",
    ).toEqual([]);
  });

  /**
   * 4.9 — the dashboard says that alerts arrive only while it is open, and does not imply more.
   *
   * <p>Pitfall 6 is the reason this is a criterion rather than a nicety. Polling is the whole
   * mechanism, and it stops when the tab does; an owner who believes Bookly will tell them about a
   * booking with the browser closed finds out otherwise through a customer standing in an empty
   * shop. Section 1 is explicit: an alert that implies Bookly can reach someone with the browser
   * closed is worse than no alert at all.
   */
  test("theDashboardSaysAlertsArriveOnlyWhileItIsOpen", async ({ page, request }) => {
    test.setTimeout(120_000);
    const owner = await newBookingOwner(request);
    const seeded = await seedBookable(request, owner, { durationMinutes: 30 });

    await signInAsBookingOwner(page, owner);
    await page.goto(`/dashboard/${seeded.businessId}`);
    const text = await settledText(page);

    expect(
      text,
      "4.9: the dashboard must state that new-booking alerts arrive only while this page is open. " +
        "Leaving it unsaid lets the owner assume the opposite, and the assumption is only " +
        "corrected by a missed appointment",
    ).toMatch(/(while|when)[^.]{0,60}\b(page|dashboard|tab|screen|window)\b[^.]{0,30}\bopen\b/i);

    expect(
      text,
      "and it must not imply a channel that does not exist: there is no push notification here",
    ).not.toMatch(/push notification/i);
    expect(
      text,
      "nor an email or SMS alert — the spec adds no backend, so nothing is sent anywhere",
    ).not.toMatch(/\b(e-?mail|sms|text message)s?\b[^.]{0,30}\b(alert|notif|remind)/i);
    expect(
      text,
      "and it must not claim to reach the owner with the browser closed, which is the exact " +
        "belief pitfall 6 exists to prevent",
    ).not.toMatch(/even (when|if|while)[^.]{0,60}\b(closed|not open|away|offline|logged out)\b/i);

    // "Unobtrusively": a notice about a convenience must not be a thing the owner has to dismiss
    // before they can work.
    await expect(
      page.getByRole("dialog"),
      "4.9 asks for this unobtrusively; a modal blocking the dashboard is the opposite",
    ).toHaveCount(0);
  });
});
