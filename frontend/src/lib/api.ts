/**
 * The single place the browser talks to the API.
 *
 * <p>Every failure arrives as the backend's one error shape, so screens branch on a stable `code`
 * rather than on a message string that will be reworded one day.
 */
const BASE_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export type ApiErrorBody = {
  code: string;
  message: string;
  fieldErrors?: Record<string, string>;
};

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly body: ApiErrorBody,
  ) {
    super(body.message);
    this.name = "ApiError";
  }
}

export type TokenPair = {
  accessToken: string;
  refreshToken: string;
  expiresInSeconds: number;
};

export type Business = {
  id: string;
  name: string;
  slug: string;
  timezone: string;
};

export type UserSummary = { id: string; email: string; fullName: string };

export type ServiceOffering = {
  id: string;
  name: string;
  durationMinutes: number;
  priceMinor: number | null;
};

export type Employee = { id: string; fullName: string; serviceIds: string[] };

export type Weekday =
  | "MONDAY"
  | "TUESDAY"
  | "WEDNESDAY"
  | "THURSDAY"
  | "FRIDAY"
  | "SATURDAY"
  | "SUNDAY";

export type WorkingHours = { id: string; weekday: Weekday; startsAt: string; endsAt: string };

export type BlockedTime = {
  id: string;
  employeeId: string | null;
  startsAt: string;
  endsAt: string;
  reason: string | null;
};

/** employeeIds is present even when one employee was requested, so the shape never changes. */
export type AvailableSlot = { start: string; end: string; employeeIds: string[] };

export type PublicService = {
  id: string;
  name: string;
  durationMinutes: number;
  priceMinor: number | null;
};

export type PublicEmployee = { id: string; name: string; serviceIds: string[] };

export type PublicBusiness = {
  slug: string;
  name: string;
  timezone: string;
  services: PublicService[];
  employees: PublicEmployee[];
};

export type PublicAvailability = {
  serviceId: string;
  date: string;
  timezone: string;
  stepMinutes: number;
  slots: AvailableSlot[];
};

export type BookingConfirmation = {
  id: string;
  businessName: string;
  serviceName: string;
  employeeName: string;
  startsAt: string;
  endsAt: string;
  timezone: string;
  status: string;
};

export type Appointment = {
  id: string;
  serviceId: string;
  serviceName: string | null;
  employeeId: string;
  employeeName: string | null;
  startsAt: string;
  endsAt: string;
  status: string;
  customerName: string | null;
  customerEmail: string | null;
  customerPhone: string | null;
};

export type Availability = {
  serviceId: string;
  date: string;
  timezone: string;
  stepMinutes: number;
  slots: AvailableSlot[];
};

/**
 * Exchanges a refresh token for a new pair, so rotation actually happens in the browser.
 *
 * <p>Set by the auth provider. Without it the client never called /api/auth/refresh at all, so it
 * never presented a spent token — and the server's reuse detection, which only fires when a rotated
 * token is replayed, could never trigger for a real user. A thief could rotate at leisure and stay
 * undetected, because detection depends on the victim eventually burning the token first.
 */
let refreshHandler: (() => Promise<string | null>) | null = null;

export function setRefreshHandler(handler: (() => Promise<string | null>) | null) {
  refreshHandler = handler;
}

async function request<T>(
  path: string,
  options: {
    method?: string;
    body?: unknown;
    accessToken?: string;
    retryOn401?: boolean;
  } = {},
): Promise<T> {
  const response = await fetch(`${BASE_URL}${path}`, {
    method: options.method ?? "GET",
    headers: {
      "Content-Type": "application/json",
      ...(options.accessToken
        ? { Authorization: `Bearer ${options.accessToken}` }
        : {}),
    },
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  });

  // An expired access token is the ordinary case, not an error: rotate once and retry.
  // Guarded by retryOn401 so a failed refresh cannot recurse.
  if (
    response.status === 401 &&
    options.accessToken &&
    options.retryOn401 !== false &&
    refreshHandler
  ) {
    const renewed = await refreshHandler();
    if (renewed) {
      return request<T>(path, { ...options, accessToken: renewed, retryOn401: false });
    }
  }

  if (!response.ok) {
    // A non-JSON error body means something upstream of the application failed;
    // surface it as a failure rather than letting the parse throw a confusing one.
    const body = (await response.json().catch(() => ({
      code: "UNREACHABLE",
      message: "The server could not be reached.",
    }))) as ApiErrorBody;
    throw new ApiError(response.status, body);
  }

  return response.status === 204 ? (undefined as T) : ((await response.json()) as T);
}

export const api = {
  register: (body: { email: string; password: string; fullName: string }) =>
    request<UserSummary>("/api/auth/register", { method: "POST", body }),

  login: (body: { email: string; password: string }) =>
    request<TokenPair>("/api/auth/login", { method: "POST", body }),

  refresh: (refreshToken: string) =>
    request<TokenPair>("/api/auth/refresh", {
      method: "POST",
      body: { refreshToken },
    }),

  logout: (refreshToken: string) =>
    request<void>("/api/auth/logout", { method: "POST", body: { refreshToken } }),

  listBusinesses: (accessToken: string) =>
    request<Business[]>("/api/businesses", { accessToken }),

  createBusiness: (accessToken: string, body: { name: string; timezone: string }) =>
    request<Business>("/api/businesses", { method: "POST", body, accessToken }),

  getBusiness: (accessToken: string, businessId: string) =>
    request<Business>(`/api/businesses/${businessId}`, { accessToken }),

  listServices: (accessToken: string, businessId: string) =>
    request<ServiceOffering[]>(`/api/businesses/${businessId}/services`, { accessToken }),

  createService: (
    accessToken: string,
    businessId: string,
    body: { name: string; durationMinutes: number },
  ) =>
    request<ServiceOffering>(`/api/businesses/${businessId}/services`, {
      method: "POST",
      body,
      accessToken,
    }),

  deleteService: (accessToken: string, businessId: string, serviceId: string) =>
    request<void>(`/api/businesses/${businessId}/services/${serviceId}`, {
      method: "DELETE",
      accessToken,
    }),

  listEmployees: (accessToken: string, businessId: string) =>
    request<Employee[]>(`/api/businesses/${businessId}/employees`, { accessToken }),

  createEmployee: (accessToken: string, businessId: string, body: { fullName: string }) =>
    request<Employee>(`/api/businesses/${businessId}/employees`, {
      method: "POST",
      body,
      accessToken,
    }),

  deleteEmployee: (accessToken: string, businessId: string, employeeId: string) =>
    request<void>(`/api/businesses/${businessId}/employees/${employeeId}`, {
      method: "DELETE",
      accessToken,
    }),

  /** Replaces the whole set, so a caller never reasons about add-versus-remove. */
  setEmployeeServices: (
    accessToken: string,
    businessId: string,
    employeeId: string,
    serviceIds: string[],
  ) =>
    request<Employee>(`/api/businesses/${businessId}/employees/${employeeId}/services`, {
      method: "PUT",
      body: { serviceIds },
      accessToken,
    }),

  listWorkingHours: (accessToken: string, businessId: string, employeeId: string) =>
    request<WorkingHours[]>(
      `/api/businesses/${businessId}/employees/${employeeId}/working-hours`,
      { accessToken },
    ),

  addWorkingHours: (
    accessToken: string,
    businessId: string,
    employeeId: string,
    body: { weekday: Weekday; startsAt: string; endsAt: string },
  ) =>
    request<WorkingHours>(
      `/api/businesses/${businessId}/employees/${employeeId}/working-hours`,
      { method: "POST", body, accessToken },
    ),

  deleteWorkingHours: (accessToken: string, businessId: string, workingHoursId: string) =>
    request<void>(`/api/businesses/${businessId}/working-hours/${workingHoursId}`, {
      method: "DELETE",
      accessToken,
    }),

  listBlockedTimes: (accessToken: string, businessId: string) =>
    request<BlockedTime[]>(`/api/businesses/${businessId}/blocked-times`, { accessToken }),

  availability: (
    accessToken: string,
    businessId: string,
    params: { serviceId: string; employeeId?: string; date: string },
  ) => {
    const query = new URLSearchParams({ serviceId: params.serviceId, date: params.date });
    if (params.employeeId) {
      query.set("employeeId", params.employeeId);
    }
    return request<Availability>(
      `/api/businesses/${businessId}/availability?${query.toString()}`,
      { accessToken },
    );
  },

  listAppointments: (accessToken: string, businessId: string, from: string, to: string) =>
    request<Appointment[]>(
      `/api/businesses/${businessId}/appointments?from=${from}&to=${to}`,
      { accessToken },
    ),

  cancelAppointment: (accessToken: string, businessId: string, appointmentId: string) =>
    request<Appointment>(
      `/api/businesses/${businessId}/appointments/${appointmentId}/cancellation`,
      { method: "POST", accessToken },
    ),

  // The public surface. No token by design: requiring an account is the friction the
  // problem statement objects to.
  publicBusiness: (slug: string) =>
    request<PublicBusiness>(`/api/public/businesses/${encodeURIComponent(slug)}`),

  publicAvailability: (
    slug: string,
    params: { serviceId: string; employeeId?: string; date: string },
  ) => {
    const query = new URLSearchParams({ serviceId: params.serviceId, date: params.date });
    if (params.employeeId) {
      query.set("employeeId", params.employeeId);
    }
    return request<PublicAvailability>(
      `/api/public/businesses/${encodeURIComponent(slug)}/availability?${query.toString()}`,
    );
  },

  publicBook: (
    slug: string,
    body: {
      serviceId: string;
      employeeId: string;
      startsAt: string;
      customerName: string;
      customerEmail: string;
      customerPhone?: string;
    },
  ) =>
    request<BookingConfirmation>(
      `/api/public/businesses/${encodeURIComponent(slug)}/appointments`,
      { method: "POST", body },
    ),
};
