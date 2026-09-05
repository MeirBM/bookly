"use client";

import { zodResolver } from "@/lib/zod-resolver";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { Field, buttonClass, inputClass } from "@/components/Field";
import { FormError } from "@/components/FormError";
import { ApiError, api } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";

// Mirrors the server's constraints so the user is told before a round trip.
// The server still enforces them: this is convenience, not validation.
const schema = z.object({
  fullName: z.string().min(1, "Enter your name.").max(120),
  email: z.string().email("Enter a valid email address.").max(254),
  password: z
    .string()
    .min(12, "Use at least 12 characters — length is what resists guessing.")
    .max(128),
});

export default function RegisterPage() {
  const router = useRouter();
  const { signIn } = useAuth();
  const [failure, setFailure] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<z.infer<typeof schema>>({ resolver: zodResolver(schema) });

  return (
    <main className="mx-auto flex w-full max-w-sm flex-1 flex-col justify-center gap-6 px-6 py-16">
      <h1 className="text-2xl font-semibold">Create your account</h1>

      <form
        className="flex flex-col gap-4"
        onSubmit={handleSubmit(async (values) => {
          setFailure(null);
          try {
            await api.register(values);
            // Registering does not sign you in server-side, so exchange the same
            // credentials for tokens rather than sending the user to log in again.
            signIn(await api.login({ email: values.email, password: values.password }));
            router.replace("/dashboard");
          } catch (error) {
            setFailure(
              error instanceof ApiError
                ? error.body.message
                : "Could not reach the server. Check your connection and try again.",
            );
          }
        })}
      >
        <FormError message={failure} />

        <Field label="Your name" error={errors.fullName?.message}>
          <input className={inputClass} autoComplete="name" {...register("fullName")} />
        </Field>

        <Field label="Email" error={errors.email?.message}>
          <input className={inputClass} type="email" autoComplete="email" {...register("email")} />
        </Field>

        <Field label="Password" error={errors.password?.message}>
          <input
            className={inputClass}
            type="password"
            autoComplete="new-password"
            {...register("password")}
          />
        </Field>

        <button className={buttonClass} type="submit" disabled={isSubmitting}>
          {isSubmitting ? "Creating…" : "Create account"}
        </button>
      </form>

      <p className="text-sm text-slate-600">
        Already registered?{" "}
        <Link className="underline" href="/login">
          Sign in
        </Link>
      </p>
    </main>
  );
}
