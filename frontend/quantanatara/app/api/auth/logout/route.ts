import { cookies } from "next/headers";
import { clearSessionCookies, gateway, isSameOrigin, problem, REFRESH_COOKIE } from "@/lib/server/bff";

export async function POST(request: Request) {
  if (!isSameOrigin(request)) return problem(403, "Cross-site request refused");
  const refreshToken = (await cookies()).get(REFRESH_COOKIE)?.value;
  if (refreshToken) {
    try {
      await gateway("/auth/logout", { method: "POST", body: JSON.stringify({ refreshToken }), browserRequest: request });
    } catch {
      // The browser is signed out below even if the server-side revoke could not be delivered
    }
  }
  await clearSessionCookies();
  return new Response(null, { status: 204 });
}
