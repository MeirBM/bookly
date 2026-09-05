import { test, expect, type Page } from "@playwright/test";

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

const DASHBOARD = /\/dashboard(\/|\?|#|$)/;
const LOGIN = /\/login(\/|\?|#|$)/;

/**
 * A session the browser has no reason to consider stale: a well-formed JWT whose exp is an hour
 * away. The signature is meaningless — nothing in the browser can verify it, and the criterion is
 * about session state, not about cryptography. Building it this way keeps the test honest whether
 * the app treats the access token as an opaque string or decodes it to check expiry.
 */
function seededSession() {
  const encode = (value: object) =>
    Buffer.from(JSON.stringify(value)).toString("base64url");
  const header = encode({ alg: "HS256", typ: "JWT" });
  const payload = encode({
    sub: "11111111-1111-1111-1111-111111111111",
    iss: "bookly",
    exp: Math.floor(Date.now() / 1000) + 3600,
  });
  return {
    accessToken: `${header}.${payload}.c2lnbmF0dXJlLW5vdC12ZXJpZmllZC1pbi10aGUtYnJvd3Nlcg`,
    refreshToken: "11111111-2222-3333-4444-555555555555", // allow-secret: browser test fixture
    expiresInSeconds: 3600,
  };
}

async function seedSession(page: Page, session: unknown) {
  await page.addInitScript(
    ([key, value]) => {
      window.localStorage.setItem(key as string, value as string);
    },
    [SESSION_KEY, JSON.stringify(session)] as const,
  );
}

/** Resolves true if the browser navigated to /login within the window, false if it stayed put. */
async function wasBouncedToLogin(page: Page): Promise<boolean> {
  return page
    .waitForURL(LOGIN, { timeout: NO_REDIRECT_WINDOW })
    .then(() => true)
    .catch(() => false);
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
   * The case that gives the one above its meaning. A component that redirects unconditionally —
   * or that redirects before it has read storage, which is the defect this regresses against —
   * passes the first test perfectly while bouncing every signed-in user to login on each reload.
   */
  test("keeps a visitor with a stored session on /dashboard", async ({ page }) => {
    await seedSession(page, seededSession());

    await page.goto("/dashboard");

    expect(
      await wasBouncedToLogin(page),
      "a visitor whose session is in localStorage was redirected to /login; a redirect decided " +
        "before stored session state has been read bounces signed-in users on every reload",
    ).toBe(false);
    await expect(page).toHaveURL(DASHBOARD, { timeout: SETTLE });
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
