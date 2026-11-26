import { callAsUser, clearSessionCookies, isSameOrigin, problem, relay } from "@/lib/server/bff";

export async function POST(request: Request) {
  if (!isSameOrigin(request)) return problem(403, "Cross-site request refused");
  try {
    const response = await callAsUser(request, "/auth/logout-all", { method: "POST" });
    // Unlike plain logout, do NOT pretend success: the user must know if other devices are still signed in
    if (!response.ok) return relay(response);
    await clearSessionCookies();
    return new Response(null, { status: 204 });
  } catch {
    return problem(503, "The service is unavailable. Try again shortly.");
  }
}
