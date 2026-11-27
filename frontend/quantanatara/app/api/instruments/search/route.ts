import { callAsUser, problem, relay } from "@/lib/server/bff";

export async function GET(request: Request) {
  // Only q is passed on, re-encoded, so the browser cannot add other parameters or change the path
  const q = new URL(request.url).searchParams.get("q") ?? "";
  try {
    return relay(await callAsUser(request, `/market/instruments/search?${new URLSearchParams({ q })}`));
  } catch {
    return problem(503, "The service is unavailable. Try again shortly.");
  }
}
