import { test, expect, request as pw } from "@playwright/test";
const API = process.env.E2E_API_URL ?? "http://localhost:8080";
const P = "correct-horse-battery-staple-1";
const DATE = "2026-10-14";

test("probe booking pages", async ({ page }) => {
  const api = await pw.newContext();
  const stamp = Date.now();
  const email = `bk-${stamp}@example.test`;
  await api.post(`${API}/api/auth/register`, { data: { email, password: P, fullName: "BK" } });
  const tokens = await (await api.post(`${API}/api/auth/login`, { data: { email, password: P } })).json();
  const auth = { Authorization: `Bearer ${tokens.accessToken}` };
  const biz = await (await api.post(`${API}/api/businesses`,
    { data: { name: `Probe Book ${stamp}`, timezone: "Pacific/Auckland" }, headers: auth })).json();
  const svc = await (await api.post(`${API}/api/businesses/${biz.id}/services`,
    { data: { name: `Fade ${stamp}`, durationMinutes: 60, priceMinor: 4500 }, headers: auth })).json();
  const emp = await (await api.post(`${API}/api/businesses/${biz.id}/employees`,
    { data: { fullName: `Robin ${stamp}` }, headers: auth })).json();
  await api.put(`${API}/api/businesses/${biz.id}/employees/${emp.id}/services`,
    { data: { serviceIds: [svc.id] }, headers: auth });
  for (const d of ["MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY","SUNDAY"]) {
    await api.post(`${API}/api/businesses/${biz.id}/employees/${emp.id}/working-hours`,
      { data: { weekday: d, startsAt: "09:00:00", endsAt: "17:00:00" }, headers: auth });
  }
  console.log("### SLUG:", biz.slug, "TZ:", biz.timezone);
  const av = await (await api.get(`${API}/api/public/businesses/${biz.slug}/availability?serviceId=${svc.id}&date=${DATE}`)).json();
  console.log("### PUBLIC AVAIL:", JSON.stringify(av).slice(0, 260));

  const dump = async (label: string) => {
    await page.waitForTimeout(2200);
    const text = (await page.locator("body").innerText()).replace(/\n/g, " | ");
    console.log(`### ${label} TEXT:`, text.slice(0, 700));
    const controls = await page.evaluate(() => Array.from(
      document.querySelectorAll("input,select,button,a[href],[role=button]")).map((e) => {
        const el = e as HTMLInputElement;
        return `${e.tagName}[${el.type ?? ""}]{${(e as HTMLElement).innerText?.slice(0,40) || el.value || ""}}${e.getAttribute("name") ?? ""}`;
      }).slice(0, 30));
    console.log(`### ${label} CONTROLS:`, JSON.stringify(controls));
  };

  await page.goto(`/book/${biz.slug}`);
  await dump("BOOK");

  // try clicking through the flow
  const selects = page.locator("select");
  if (await selects.count() > 0) {
    for (let i = 0; i < await selects.count(); i++) {
      const opts = await selects.nth(i).locator("option").evaluateAll((os) =>
        os.map((o) => `${(o as HTMLOptionElement).value}=${o.textContent}`));
      console.log(`### BOOK SELECT ${i}:`, JSON.stringify(opts).slice(0, 300));
    }
  }
  const dateInput = page.locator("input[type=date]");
  if (await dateInput.count() > 0) { await dateInput.first().fill(DATE); await dump("BOOK AFTER DATE"); }

  await page.addInitScript(([k, v]) => window.localStorage.setItem(k as string, v as string),
    ["bookly.tokens", JSON.stringify(tokens)] as const);
  await page.goto(`/dashboard/${biz.id}/appointments`);
  await dump("APPOINTMENTS");
  await page.goto(`/dashboard/${biz.id}/calendar`);
  await dump("CALENDAR");
  await page.goto(`/book/definitely-not-a-slug-${stamp}`);
  await dump("BOOK 404");
  expect(true).toBe(true);
});
