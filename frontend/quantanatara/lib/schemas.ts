// Client-side validation for a friendly UI. The backend repeats every rule and is the one that counts.
import { z } from "zod";

export const loginSchema = z.object({
  email: z.email("Enter a valid email address"),
  password: z.string().min(1, "Enter your password").max(256),
});

export const registerSchema = z.object({
  email: z.email("Enter a valid email address").max(254),
  password: z
    .string()
    .min(8, "At least 8 characters")
    // BCrypt only reads 72 BYTES, so count bytes, not characters
    .refine((value) => new TextEncoder().encode(value).length <= 72, "At most 72 bytes"),
});

export const profileSchema = z.object({
  theme: z.enum(["LIGHT", "DARK", "SYSTEM"]),
  defaultExchange: z.enum(["NSE", "BSE", "NASDAQ", "NYSE"]),
  timezone: z.string().min(1).max(64),
});

export type Credentials = z.infer<typeof loginSchema>;
export type Preferences = z.infer<typeof profileSchema>;
export type Profile = Preferences & { email: string; role: string };

/** Turns a problem-details error body from the API into one readable line. */
export async function errorMessage(response: Response): Promise<string> {
  const retryAfter = response.headers.get("retry-after");
  if (response.status === 429) {
    return retryAfter ? `Too many attempts. Try again in ${retryAfter} seconds.` : "Too many requests. Wait a moment and try again.";
  }
  try {
    const body = (await response.json()) as { detail?: string; errors?: string[] };
    return body.errors?.join("; ") ?? body.detail ?? `Request failed (${response.status})`;
  } catch {
    return `Request failed (${response.status})`;
  }
}
