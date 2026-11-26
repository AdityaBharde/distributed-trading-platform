"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { buttonVariants } from "@/components/ui/button";
import { errorMessage, loginSchema, registerSchema, type Credentials } from "@/lib/schemas";

function post(url: string, body: Credentials) {
  return fetch(url, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) });
}

export function AuthForm({ mode }: { mode: "login" | "register" }) {
  const router = useRouter();
  const [serverError, setServerError] = useState<string | null>(null);
  const isRegister = mode === "register";
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<Credentials>({ resolver: zodResolver(isRegister ? registerSchema : loginSchema) });

  async function onSubmit(values: Credentials) {
    setServerError(null);
    try {
      if (isRegister) {
        const created = await post("/api/auth/register", values);
        if (!created.ok) {
          setServerError(await errorMessage(created));
          return;
        }
      }
      const response = await post("/api/auth/login", values);
      if (!response.ok) {
        setServerError(await errorMessage(response));
        return;
      }
      router.push("/profile");
      router.refresh();
    } catch {
      setServerError("Could not reach the server. Check your connection.");
    }
  }

  const input = "w-full rounded-lg border border-input bg-background px-3 py-2 text-sm";

  return (
    <main className="mx-auto flex w-full max-w-sm flex-1 flex-col justify-center gap-6 p-6">
      <h1 className="text-2xl font-bold">{isRegister ? "Create your account" : "Sign in"}</h1>

      <form onSubmit={handleSubmit(onSubmit)} noValidate className="flex flex-col gap-4">
        <label className="flex flex-col gap-1 text-sm font-medium">
          Email
          <input type="email" autoComplete="email" className={input} {...register("email")} />
          {errors.email && <span className="text-destructive">{errors.email.message}</span>}
        </label>

        <label className="flex flex-col gap-1 text-sm font-medium">
          Password
          <input
            type="password"
            autoComplete={isRegister ? "new-password" : "current-password"}
            className={input}
            {...register("password")}
          />
          {errors.password && <span className="text-destructive">{errors.password.message}</span>}
        </label>

        {serverError && (
          <p role="alert" className="text-sm text-destructive">
            {serverError}
          </p>
        )}

        <button type="submit" disabled={isSubmitting} className={buttonVariants({ size: "lg" })}>
          {isSubmitting ? "Please wait..." : isRegister ? "Create account" : "Sign in"}
        </button>
      </form>

      <p className="text-sm text-muted-foreground">
        {isRegister ? "Already have an account? " : "New here? "}
        <Link className="underline" href={isRegister ? "/login" : "/register"}>
          {isRegister ? "Sign in" : "Create an account"}
        </Link>
      </p>
    </main>
  );
}
