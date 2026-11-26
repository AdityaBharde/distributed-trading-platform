// Next.js 16 "proxy" (formerly middleware). An OPTIMISTIC check only: it looks for a session cookie so that
// signed-out visitors are redirected before the page loads. It does not verify anything; the real protection is
// the gateway and services rejecting requests without a valid token.
import { NextResponse, type NextRequest } from "next/server";

export function proxy(request: NextRequest) {
  const hasSession = request.cookies.has("qt_refresh");
  const { pathname } = request.nextUrl;

  if (pathname.startsWith("/profile") && !hasSession) {
    return NextResponse.redirect(new URL("/login", request.url));
  }
  if ((pathname === "/login" || pathname === "/register") && hasSession) {
    return NextResponse.redirect(new URL("/profile", request.url));
  }
  return NextResponse.next();
}

export const config = {
  matcher: ["/profile/:path*", "/login", "/register"],
};
