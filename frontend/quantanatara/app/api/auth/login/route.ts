import { gateway, isSameOrigin, problem, relay, setSessionCookies, type Tokens } from "@/lib/server/bff";

export async function POST(request: Request) {
  if (!isSameOrigin(request)) return problem(403, "Cross-site request refused");
  try {
    const response = await gateway("/auth/login", { method: "POST", body: await request.text(), browserRequest: request });
    if (!response.ok) return relay(response);
    // The tokens stop here: they go into httpOnly cookies and are never part of the body the browser's JS sees.
    await setSessionCookies((await response.json()) as Tokens);
    return Response.json({ ok: true });
  } catch {
    return problem(503, "The service is unavailable. Try again shortly.");
  }
}
