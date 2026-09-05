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
  webServer: {
    // next start does not work with output: "standalone" - it warns and serves something
    // other than what ships. Running the standalone server, with the static assets copied
    // beside it exactly as the Dockerfile does, means these tests exercise the deployed
    // artifact rather than a development approximation of it.
    command: "npm run build:standalone && node .next/standalone/server.js",
    url: "http://localhost:3000",
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
});
