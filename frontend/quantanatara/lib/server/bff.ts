// Backend-for-frontend helpers. SERVER ONLY: imported by route handlers, never by client components.
// The browser talks to /api/* on this Next.js server; only this code talks to the gateway and sees tokens.
import { cookies } from "next/headers";

export const ACCESS_COOKIE = "qt_access";
export const REFRESH_COOKIE = "qt_refresh";

const GATEWAY_URL = process.env.GATEWAY_URL ?? "http://localhost:8080";
const BFF_SHARED_SECRET = process.env.BFF_SHARED_SECRET ?? "";

export type Tokens = { accessToken: string; refreshToken: string; expiresIn: number };

type GatewayInit = { method?: string; body?: string; accessToken?: string; browserRequest?: Request };

/** One fetch to the API gateway. Throws only if the gateway cannot be reached. */
export function gateway(path: string, init: GatewayInit = {}): Promise<Response> {
  const headers = new Headers();
  if (init.body !== undefined) headers.set("Content-Type", "application/json");
  if (init.accessToken) headers.set("Authorization", `Bearer ${init.accessToken}`);

  // Auth endpoints are rate limited per IP. Tell the gateway the BROWSER's address, proving we are the BFF.
  // (In production a reverse proxy in front of Next.js must overwrite X-Forwarded-For, or a browser could fake it.)
  const clientIp = init.browserRequest?.headers.get("x-forwarded-for")?.split(",")[0]?.trim();
  if (path.startsWith("/auth/") && clientIp && BFF_SHARED_SECRET) {
    headers.set("X-Forwarded-For", clientIp);
    headers.set("X-BFF-Secret", BFF_SHARED_SECRET);
  }

  return fetch(`${GATEWAY_URL}${path}`, {
    method: init.method ?? "GET",
    headers,
    body: init.body,
    cache: "no-store",
  });
}

/** Passes a gateway answer (status, JSON body, Retry-After) on to the browser. */
export async function relay(response: Response): Promise<Response> {
  const headers = new Headers();
  const retryAfter = response.headers.get("retry-after");
  if (retryAfter) headers.set("Retry-After", retryAfter);
  const text = await response.text();
  if (text) headers.set("Content-Type", response.headers.get("content-type") ?? "application/json");
  return new Response(text || null, { status: response.status, headers });
}

export function problem(status: number, detail: string): Response {
  return Response.json({ status, detail }, { status });
}

/**
 * CSRF defence for state-changing requests: cookies are sent automatically by the browser, so we also require
 * that the Origin header (which a page cannot forge) is our own host. SameSite=Lax is the second layer.
 */
export function isSameOrigin(request: Request): boolean {
  const origin = request.headers.get("origin");
  const host = request.headers.get("host");
  if (!origin || !host) return false;
  try {
    return new URL(origin).host === host;
  } catch {
    return false;
  }
}

/** Seconds until the JWT expires. Only used to set the cookie lifetime; the backend does the real checking. */
function secondsUntilExpiry(jwt: string): number {
  const payload = JSON.parse(Buffer.from(jwt.split(".")[1], "base64url").toString("utf8")) as { exp: number };
  return Math.max(0, Math.floor(payload.exp - Date.now() / 1000));
}

/** httpOnly: page JavaScript (and so any XSS) cannot read the tokens. Only callable inside a route handler. */
export async function setSessionCookies(tokens: Tokens): Promise<void> {
  const store = await cookies();
  const common = {
    httpOnly: true,
    sameSite: "lax" as const,
    secure: process.env.NODE_ENV === "production",
    path: "/",
  };
  store.set(ACCESS_COOKIE, tokens.accessToken, { ...common, maxAge: tokens.expiresIn });
  // The refresh token keeps the session's original 30-day end date, so the cookie follows it
  store.set(REFRESH_COOKIE, tokens.refreshToken, { ...common, maxAge: secondsUntilExpiry(tokens.refreshToken) });
}

export async function clearSessionCookies(): Promise<void> {
  const store = await cookies();
  store.delete(ACCESS_COOKIE);
  store.delete(REFRESH_COOKIE);
}

type RefreshResult = { ok: true; tokens: Tokens } | { ok: false; status: number };

// Refresh tokens are single-use: two parallel refreshes with the same token look like theft and kill the session.
// So concurrent requests share ONE refresh call, and the result is kept briefly for requests that were already
// on their way with the old cookie. (In memory: fine for one Next.js instance; more instances need shared storage.)
const inFlight = new Map<string, Promise<RefreshResult>>();
const KEEP_RESULT_MS = 10_000;

function refreshOnce(refreshToken: string, browserRequest: Request): Promise<RefreshResult> {
  let pending = inFlight.get(refreshToken);
  if (!pending) {
    pending = (async (): Promise<RefreshResult> => {
      const response = await gateway("/auth/refresh", {
        method: "POST",
        body: JSON.stringify({ refreshToken }),
        browserRequest,
      });
      if (!response.ok) return { ok: false, status: response.status };
      return { ok: true, tokens: (await response.json()) as Tokens };
    })();
    inFlight.set(refreshToken, pending);
    pending.then(
      () => setTimeout(() => inFlight.delete(refreshToken), KEEP_RESULT_MS),
      () => inFlight.delete(refreshToken),
    );
  }
  return pending;
}

/**
 * Calls a protected gateway endpoint as the logged-in user, transparently refreshing an expired access token.
 * Returns 401 (and clears the cookies) only when the session is really over.
 */
export async function callAsUser(
  request: Request,
  path: string,
  init: { method?: string; body?: string } = {},
): Promise<Response> {
  const store = await cookies();
  let accessToken = store.get(ACCESS_COOKIE)?.value;
  const refreshToken = store.get(REFRESH_COOKIE)?.value;

  if (accessToken) {
    const response = await gateway(path, { ...init, accessToken });
    if (response.status !== 401) return response;
  }

  if (!refreshToken) {
    await clearSessionCookies();
    return problem(401, "Not signed in");
  }

  const refreshed = await refreshOnce(refreshToken, request);
  if (!refreshed.ok) {
    // 401 = session over. Anything else (429, 503) is temporary: keep the cookies and let the user retry.
    if (refreshed.status === 401) {
      await clearSessionCookies();
      return problem(401, "Session expired. Please sign in again.");
    }
    return problem(refreshed.status, "Could not renew your session. Try again shortly.");
  }

  await setSessionCookies(refreshed.tokens);
  accessToken = refreshed.tokens.accessToken;
  return gateway(path, { ...init, accessToken });
}
