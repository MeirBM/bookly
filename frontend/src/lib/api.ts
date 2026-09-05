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

async function request<T>(
  path: string,
  options: { method?: string; body?: unknown; accessToken?: string } = {},
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
};
