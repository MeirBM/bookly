"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { Field, buttonClass, inputClass } from "@/components/Field";
import { FormError } from "@/components/FormError";
import { ApiError, api } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";

const schema = z.object({
  email: z.string().email("Enter a valid email address."),
  password: z.string().min(1, "Enter your password."),
});

export default function LoginPage() {
  const router = useRouter();
  const { signIn, tokens, ready } = useAuth();
  const [failure, setFailure] = useState<string | null>(null);

  useEffect(() => {
    if (ready && tokens) {
      router.replace("/dashboard");
    }
  }, [ready, tokens, router]);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<z.infer<typeof schema>>({ resolver: zodResolver(schema) });

  return (
    <main className="mx-auto flex w-full max-w-sm flex-1 flex-col justify-center gap-6 px-6 py-16">
      <h1 className="text-2xl font-semibold">Sign in</h1>

      <form
        className="flex flex-col gap-4"
        onSubmit={handleSubmit(async (values) => {
          setFailure(null);
          try {
            signIn(await api.login(values));
            router.replace("/dashboard");
          } catch (error) {
            // The server deliberately does not say whether the account exists, and
            // this message must not become more specific than the server's answer.
            setFailure(
              error instanceof ApiError
                ? error.body.message
                : "Could not reach the server. Check your connection and try again.",
            );
          }
        })}
      >
        <FormError message={failure} />

        <Field label="Email" error={errors.email?.message}>
          <input className={inputClass} type="email" autoComplete="email" {...register("email")} />
        </Field>

        <Field label="Password" error={errors.password?.message}>
          <input
            className={inputClass}
            type="password"
            autoComplete="current-password"
            {...register("password")}
          />
        </Field>

        <button className={buttonClass} type="submit" disabled={isSubmitting}>
          {isSubmitting ? "Signing in…" : "Sign in"}
        </button>
      </form>

      <p className="text-sm text-slate-600">
        No account?{" "}
        <Link className="underline" href="/register">
          Create one
        </Link>
      </p>
    </main>
  );
}
