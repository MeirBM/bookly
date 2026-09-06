import { expect, type APIRequestContext, type Page } from "@playwright/test";

/**
 * Seeding for the browser suites, through the authenticated API.
 *
 * <p>Each test builds its own business, so no test depends on a shared fixture or on the order the
 * suite happens to run in. Nothing here reads frontend/src: the shapes come from
 * docs/api/turn-3-openapi.json.
 */

export const API = process.env.E2E_API_URL ?? "http://localhost:8080";
export const SESSION_KEY = "bookly.tokens";

const PASSWORD = "correct-horse-battery-staple-1"; // allow-secret: browser test fixture

export type Owner = {
  tokens: { accessToken: string; refreshToken: string };
  auth: Record<string, string>;
};

export type WorkingWindow = { weekday: string; start: string; end: string };

export type Seeded = {
  owner: Owner;
  businessId: string;
  businessName: string;
  slug: string;
  timezone: string;
  serviceId: string;
  serviceName: string;
  durationMinutes: number;
  employeeId: string;
  employeeName: string;
  otherEmployeeId: string;
  otherEmployeeName: string;
};

export const EVERY_WEEKDAY: WorkingWindow[] = [
  "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY",
].map((weekday) => ({ weekday, start: "09:00:00", end: "17:00:00" }));

function stamp(): string {
  return `${Date.now()}-${Math.floor(Math.random() * 1e6)}`;
}

export async function newOwner(request: APIRequestContext): Promise<Owner> {
  const email = `e2e-${stamp()}@example.test`;
  const registered = await request.post(`${API}/api/auth/register`, {
    data: { email, password: PASSWORD, fullName: "Browser Owner" },
  });
  expect(registered.status(), "seeding: register").toBe(201);
  const loggedIn = await request.post(`${API}/api/auth/login`, { data: { email, password: PASSWORD } });
  expect(loggedIn.status(), "seeding: login").toBe(200);
  const tokens = await loggedIn.json();
  return { tokens, auth: { Authorization: `Bearer ${tokens.accessToken}` } };
}

/**
 * A business a visitor could actually book. "Bookable" is stricter since 3.17/3.29: it needs a
 * service *and* somebody who performs it *and* working hours, or the public page answers 404.
 */
export async function seedBookable(
  request: APIRequestContext,
  owner: Owner,
  options: {
    timezone?: string;
    durationMinutes?: number;
    hours?: WorkingWindow[];
    people?: number;
  } = {},
): Promise<Seeded> {
  const timezone = options.timezone ?? "Pacific/Auckland";
  const durationMinutes = options.durationMinutes ?? 30;
  const hours = options.hours ?? EVERY_WEEKDAY;
  const people = options.people ?? 1;
  const id = stamp();

  const businessName = `Browser Salon ${id}`;
  const business = await request.post(`${API}/api/businesses`, {
    data: { name: businessName, timezone },
    headers: owner.auth,
  });
  expect(business.status(), "seeding: create business").toBe(201);
  const created = await business.json();

  const serviceName = `Signature Trim ${id}`;
  const service = await request.post(`${API}/api/businesses/${created.id}/services`, {
    data: { name: serviceName, durationMinutes, priceMinor: 4500 },
    headers: owner.auth,
  });
  expect(service.status(), "seeding: create service").toBe(201);
  const serviceId = (await service.json()).id;

  const staff: { id: string; name: string }[] = [];
  for (let i = 0; i < Math.max(people, 1); i++) {
    const employeeName = `${i === 0 ? "Robin" : "Alex"} Cutter ${id}-${i}`;
    const employee = await request.post(`${API}/api/businesses/${created.id}/employees`, {
      data: { fullName: employeeName },
      headers: owner.auth,
    });
    expect(employee.status(), "seeding: create employee").toBe(201);
    const employeeId = (await employee.json()).id;
    await request.put(`${API}/api/businesses/${created.id}/employees/${employeeId}/services`, {
      data: { serviceIds: [serviceId] },
      headers: owner.auth,
    });
    for (const window of hours) {
      const added = await request.post(
        `${API}/api/businesses/${created.id}/employees/${employeeId}/working-hours`,
        { data: { weekday: window.weekday, startsAt: window.start, endsAt: window.end }, headers: owner.auth },
      );
      expect(added.status(), `seeding: working hours ${window.weekday}`).toBe(201);
    }
    staff.push({ id: employeeId, name: employeeName });
  }

  return {
    owner,
    businessId: created.id,
    businessName,
    slug: created.slug,
    timezone: created.timezone,
    serviceId,
    serviceName,
    durationMinutes,
    employeeId: staff[0].id,
    employeeName: staff[0].name,
    otherEmployeeId: staff[staff.length - 1].id,
    otherEmployeeName: staff[staff.length - 1].name,
  };
}

/** A business that exists but has nobody who can serve anyone: 3.17 makes it a 404 publicly. */
export async function seedUnbookable(request: APIRequestContext, owner: Owner): Promise<{ slug: string }> {
  const business = await request.post(`${API}/api/businesses`, {
    data: { name: `Never Opened ${stamp()}`, timezone: "UTC" },
    headers: owner.auth,
  });
  expect(business.status(), "seeding: create unbookable business").toBe(201);
  const created = await business.json();
  // A service with nobody to perform it is still nothing anyone can book.
  await request.post(`${API}/api/businesses/${created.id}/services`, {
    data: { name: "Orphaned Service", durationMinutes: 30, priceMinor: 100 },
    headers: owner.auth,
  });
  return { slug: created.slug };
}

export async function signIn(page: Page, owner: Owner) {
  await page.addInitScript(
    ([key, value]) => window.localStorage.setItem(key as string, value as string),
    [SESSION_KEY, JSON.stringify(owner.tokens)] as const,
  );
}

export type PublicSlot = { start: string; end: string; employeeIds: string[] };

export async function publicAvailability(
  request: APIRequestContext,
  slug: string,
  serviceId: string,
  date: string,
  employeeId?: string,
): Promise<{ timezone: string; stepMinutes: number; slots: PublicSlot[] }> {
  const query = `?serviceId=${serviceId}&date=${date}${employeeId ? `&employeeId=${employeeId}` : ""}`;
  const response = await request.get(`${API}/api/public/businesses/${slug}/availability${query}`);
  expect(response.status(), "the engine answers the public availability route").toBe(200);
  return response.json();
}

/** Books out of band, as a different visitor would from another browser. */
export async function bookViaPublicApi(
  request: APIRequestContext,
  slug: string,
  booking: { serviceId: string; employeeId: string; startsAt: string; name?: string },
) {
  return request.post(`${API}/api/public/businesses/${slug}/appointments`, {
    data: {
      serviceId: booking.serviceId,
      employeeId: booking.employeeId,
      startsAt: booking.startsAt,
      customerName: booking.name ?? "Out Of Band Visitor",
      customerEmail: `oob-${stamp()}@example.test`,
      customerPhone: "+64211111111",
    },
  });
}

export async function ownersAppointments(
  request: APIRequestContext,
  owner: Owner,
  businessId: string,
  from: string,
  to: string,
) {
  const response = await request.get(
    `${API}/api/businesses/${businessId}/appointments?from=${from}&to=${to}`,
    { headers: owner.auth },
  );
  expect(response.status(), "the owner's appointment list").toBe(200);
  return response.json();
}

/** The wall-clock time an instant refers to in the given zone, as the page would print it. */
export function localTimeIn(instant: string, timeZone: string): string {
  return new Intl.DateTimeFormat("en-GB", {
    timeZone,
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(new Date(instant));
}

export function localDateIn(instant: string, timeZone: string): string {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date(instant));
}
