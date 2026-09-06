"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { AuthShell } from "@/components/auth/AuthShell";
import { Field } from "@/components/Field";
import { FormError } from "@/components/FormError";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { ApiError, api } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { zodResolver } from "@/lib/zod-resolver";

// Mirrors the server's constraints so the reader is told before a round trip. The server still
// enforces them: this is convenience, not validation.
const schema = z.object({
  fullName: z.string().min(1, "Enter your name.").max(120),
  email: z.string().email("Enter a valid email address.").max(254),
  password: z
    .string()
    .min(12, "Use at least 12 characters — length is what resists guessing.")
    .max(72),
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
    <AuthShell
      title="Create your account"
      subtitle="Set up a business and start taking bookings on your own page."
      footer={{ prompt: "Already registered?", href: "/login", label: "Sign in" }}
    >
      <form
        className="flex flex-col gap-4"
        onSubmit={handleSubmit(async (values) => {
          setFailure(null);
          try {
            await api.register(values);
            // Registering does not sign you in server-side, so exchange the same credentials for
            // tokens rather than sending someone to a login form they just filled in.
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
          <Input autoComplete="name" {...register("fullName")} />
        </Field>
        <Field label="Email" error={errors.email?.message}>
          <Input type="email" autoComplete="email" {...register("email")} />
        </Field>
        <Field
          label="Password"
          error={errors.password?.message}
          hint="At least 12 characters. A passphrase beats a short complicated one."
        >
          <Input type="password" autoComplete="new-password" {...register("password")} />
        </Field>
        <Button type="submit" fullWidth loading={isSubmitting}>
          {isSubmitting ? "Creating…" : "Create account"}
        </Button>
      </form>
    </AuthShell>
  );
}
