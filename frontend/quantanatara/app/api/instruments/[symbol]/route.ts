import { callAsUser, problem, relay } from "@/lib/server/bff";

// NSE symbols use letters, digits, "&" and "-". Refusing everything else also stops values such as ".." from
// changing which gateway path is called with the user's token.
const SYMBOL = /^[A-Za-z0-9&-]{1,32}$/;

export async function GET(request: Request, { params }: { params: Promise<{ symbol: string }> }) {
  const { symbol: raw } = await params;
  const symbol = raw.replace(/%26/gi, "&"); // "M&M" may arrive still encoded
  if (!SYMBOL.test(symbol)) return problem(404, "No such stock");
  try {
    return relay(await callAsUser(request, `/market/instruments/${encodeURIComponent(symbol)}`));
  } catch {
    return problem(503, "The service is unavailable. Try again shortly.");
  }
}
