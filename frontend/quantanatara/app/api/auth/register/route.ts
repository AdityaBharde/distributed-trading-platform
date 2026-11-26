import { gateway, isSameOrigin, problem, relay } from "@/lib/server/bff";

export async function POST(request: Request) {
  if (!isSameOrigin(request)) return problem(403, "Cross-site request refused");
  try {
    return relay(await gateway("/auth/register", { method: "POST", body: await request.text(), browserRequest: request }));
  } catch {
    return problem(503, "The service is unavailable. Try again shortly.");
  }
}
