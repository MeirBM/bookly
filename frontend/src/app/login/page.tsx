"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
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
    <AuthShell
      title="Sign in"
      subtitle="Manage your services, your people and your bookings."
      footer={{ prompt: "No account?", href: "/register", label: "Create one" }}
    >
      <form
        className="flex flex-col gap-4"
        onSubmit={handleSubmit(async (values) => {
          setFailure(null);
          try {
            signIn(await api.login(values));
            router.replace("/dashboard");
          } catch (error) {
            // The server deliberately does not say whether the account exists, and this message
            // must not become more specific than the server's answer.
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
          <Input type="email" autoComplete="email" {...register("email")} />
        </Field>
        <Field label="Password" error={errors.password?.message}>
          <Input type="password" autoComplete="current-password" {...register("password")} />
        </Field>
        <Button type="submit" fullWidth loading={isSubmitting}>
          {isSubmitting ? "Signing in…" : "Sign in"}
        </Button>
      </form>
    </AuthShell>
  );
}
