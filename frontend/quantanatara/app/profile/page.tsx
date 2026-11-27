"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { AppHeader } from "@/components/app-header";
import { buttonVariants } from "@/components/ui/button";
import { errorMessage, profileSchema, type Preferences, type Profile } from "@/lib/schemas";

function applyTheme(theme: Preferences["theme"]) {
  const dark = theme === "DARK" || (theme === "SYSTEM" && window.matchMedia("(prefers-color-scheme: dark)").matches);
  document.documentElement.classList.toggle("dark", dark);
}

export default function ProfilePage() {
  const router = useRouter();
  const [profile, setProfile] = useState<Profile | null>(null);
  const [zones, setZones] = useState<string[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<Preferences>({ resolver: zodResolver(profileSchema) });

  const toLogin = useCallback(() => {
    router.replace("/login");
    router.refresh();
  }, [router]);

  const load = useCallback(async () => {
    try {
      const response = await fetch("/api/profile");
      if (response.status === 401) return toLogin();
      if (!response.ok) return setError(await errorMessage(response));
      const data = (await response.json()) as Profile;
      // The browser's zone list may use older names (e.g. Asia/Calcutta), so always include the saved value
      setZones(Array.from(new Set([data.timezone, ...Intl.supportedValuesOf("timeZone"), "UTC"])).sort());
      setProfile(data);
      setError(null);
      reset({ theme: data.theme, defaultExchange: data.defaultExchange, timezone: data.timezone });
      applyTheme(data.theme);
    } catch {
      setError("Could not reach the server.");
    }
  }, [reset, toLogin]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- state is set after the fetch resolves
    void load();
  }, [load]);

  async function save(values: Preferences) {
    setSaved(false);
    setError(null);
    try {
      const response = await fetch("/api/profile", {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(values),
      });
      if (response.status === 401) return toLogin();
      if (!response.ok) return setError(await errorMessage(response));
      setProfile((await response.json()) as Profile);
      applyTheme(values.theme);
      setSaved(true);
    } catch {
      setError("Could not reach the server.");
    }
  }

  async function signOut(url: "/api/auth/logout" | "/api/auth/logout-all") {
    setError(null);
    try {
      const response = await fetch(url, { method: "POST" });
      if (response.ok || response.status === 401) return toLogin();
      setError(await errorMessage(response));
    } catch {
      setError("Could not reach the server. You are still signed in.");
    }
  }

  const input = "w-full rounded-lg border border-input bg-background px-3 py-2 text-sm";

  if (!profile) {
    return (
      <main className="mx-auto flex w-full max-w-md flex-col gap-4 p-6">
        {error ? (
          <>
            <p role="alert" className="text-destructive">{error}</p>
            <button type="button" onClick={() => void load()} className={buttonVariants({ variant: "outline" })}>
              Try again
            </button>
          </>
        ) : (
          <p className="text-muted-foreground">Loading your profile...</p>
        )}
      </main>
    );
  }

  return (
    <>
    <AppHeader />
    <main className="mx-auto flex w-full max-w-md flex-col gap-6 p-6">
      <header className="flex flex-col gap-1">
        <h1 className="text-2xl font-bold">Your profile</h1>
        <p className="text-sm text-muted-foreground">
          {profile.email} · {profile.role}
        </p>
      </header>

      <form onSubmit={handleSubmit(save)} className="flex flex-col gap-4">
        <label className="flex flex-col gap-1 text-sm font-medium">
          Theme
          <select className={input} {...register("theme")}>
            <option value="SYSTEM">Follow system</option>
            <option value="LIGHT">Light</option>
            <option value="DARK">Dark</option>
          </select>
        </label>

        <label className="flex flex-col gap-1 text-sm font-medium">
          Default exchange
          <select className={input} {...register("defaultExchange")}>
            {["NSE", "BSE", "NASDAQ", "NYSE"].map((exchange) => (
              <option key={exchange} value={exchange}>{exchange}</option>
            ))}
          </select>
        </label>

        <label className="flex flex-col gap-1 text-sm font-medium">
          Time zone
          <select className={input} {...register("timezone")}>
            {zones.map((zone) => (
              <option key={zone} value={zone}>{zone}</option>
            ))}
          </select>
          {errors.timezone && <span className="text-destructive">{errors.timezone.message}</span>}
        </label>

        {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
        {saved && <p role="status" className="text-sm">Preferences saved.</p>}

        <button type="submit" disabled={isSubmitting} className={buttonVariants({ size: "lg" })}>
          {isSubmitting ? "Saving..." : "Save preferences"}
        </button>
      </form>

      <div className="flex flex-wrap items-center gap-3 border-t border-border pt-4">
        <button type="button" onClick={() => void signOut("/api/auth/logout")} className={buttonVariants({ variant: "outline" })}>
          Sign out
        </button>
        <button type="button" onClick={() => void signOut("/api/auth/logout-all")} className={buttonVariants({ variant: "destructive" })}>
          Sign out on all devices
        </button>
        <Link href="/" className="ml-auto text-sm underline">
          Trading console
        </Link>
      </div>
    </main>
    </>
  );
}
