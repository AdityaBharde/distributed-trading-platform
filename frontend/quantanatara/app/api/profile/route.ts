import { callAsUser, isSameOrigin, problem, relay } from "@/lib/server/bff";

export async function GET(request: Request) {
  try {
    return relay(await callAsUser(request, "/profile/me"));
  } catch {
    return problem(503, "The service is unavailable. Try again shortly.");
  }
}

export async function PUT(request: Request) {
  if (!isSameOrigin(request)) return problem(403, "Cross-site request refused");
  try {
    const body = await request.text();
    return relay(await callAsUser(request, "/profile/me", { method: "PUT", body }));
  } catch {
    return problem(503, "The service is unavailable. Try again shortly.");
  }
}
