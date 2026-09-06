import { defineConfig, devices } from "@playwright/test";

/**
 * Browser tests for the criteria no backend test can decide.
 *
 * <p>Criterion 1.17 is the first: whether an unauthenticated visitor reaches the dashboard is a
 * question about what the browser does with client-side session state, and it is invisible to
 * MockMvc or a REST client. Turn 3 needs the same harness for the public booking flow, where "a
 * slot taken between page load and submit shows a clear message" is likewise only answerable here.
 *
 * <p>The app is built and served rather than run in dev mode, so the tests exercise what actually
 * ships. No backend is started: these tests deliberately cover only behaviour that does not need
 * one, and any that does will seed its own state explicitly.
 */
export default defineConfig({
  testDir: "./e2e",
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: 0,
  reporter: process.env.CI ? "github" : "list",
  use: {
    baseURL: "http://localhost:3000",
    trace: "on-first-retry",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  // Two servers, because the turn-2 screens are only meaningful against a real API: the
  // dashboard's states are what the backend actually answers, and a mocked one would assert
  // that our fixtures match our fixtures.
  webServer: [
    {
      // Requires DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD and JWT_SECRET in the
      // environment, and Postgres and Redis reachable - `docker compose up -d postgres redis`.
      //
      //   set -a && . ../.env && set +a && npm run test:e2e
      //
      // Without them Playwright reports only "Process from config.webServer was not able to
      // start. Exit code: 1", which names nothing useful - the application refuses to boot
      // without a signing key, by design, and that refusal is what you are seeing.
      //
      // reuseExistingServer means a backend already running is used as-is - including one
      // started without the raised limits below, in which case seeding hits the production
      // rate limit and tests fail on 429 in milliseconds.
      command: "cd ../backend && ./mvnw -q -DskipTests spring-boot:run",
      url: "http://localhost:8080/actuator/health",
      reuseExistingServer: true,
      timeout: 180_000,
      stdout: "ignore",
      stderr: "pipe",
      env: {
        // Every test seeds its own account, and four workers do it at once, so the production
        // auth limit of 20/minute refuses the setup rather than anything under test. Raised
        // here for the same reason the test profile raises it: the limiter's own behaviour is
        // verified by AuthRateLimitIT against its own deliberately low limit, so this weakens
        // no gate — it stops one control from masking every other assertion.
        BOOKLY_SECURITY_RATELIMIT_MAXREQUESTS: "100000",
        BOOKLY_SECURITY_RATELIMIT_APIMAXREQUESTS: "100000",
        // The public surface has the strictest limit by design (criterion 3.15), and the booking
        // tests drive it hardest - availability on every date change, plus out-of-band bookings to
        // stage the race in 3.19. Omitting this made the suite's determinism depend on which
        // backend happened to be listening on :8080.
        BOOKLY_SECURITY_RATELIMIT_PUBLICMAXREQUESTS: "100000",
      },
    },
    {
      // next start does not work with output: "standalone" - it warns and serves something
      // other than what ships. Running the standalone server, with the static assets copied
      // beside it exactly as the Dockerfile does, means these tests exercise the deployed
      // artifact rather than a development approximation of it.
      command: "npm run build:standalone && node .next/standalone/server.js",
      url: "http://localhost:3000",
      reuseExistingServer: !process.env.CI,
      timeout: 120_000,
      // Next inlines this at build time, so it has to be present for the build, not the run.
      env: {
        NEXT_PUBLIC_API_URL: process.env.E2E_API_URL ?? "http://localhost:8080",
      },
    },
  ],
});
