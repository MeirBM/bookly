"use client";

import { zodResolver } from "@/lib/zod-resolver";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { Field, buttonClass, inputClass } from "@/components/Field";
import { FormError } from "@/components/FormError";
import { ApiError, api } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";

const schema = z.object({
  name: z.string().min(1, "Give the business a name.").max(120),
  timezone: z.string().min(1, "Choose a time zone."),
});

/**
 * Four states, all distinguishable: loading, error, empty, content. The empty state is the one an
 * agent skips, and it is the state every new account starts in.
 */
export default function DashboardPage() {
  const { tokens } = useAuth();
  const accessToken = tokens?.accessToken ?? "";
  const queryClient = useQueryClient();
  const [failure, setFailure] = useState<string | null>(null);

  const businesses = useQuery({
    queryKey: ["businesses"],
    queryFn: () => api.listBusinesses(accessToken),
    enabled: Boolean(accessToken),
  });

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<z.infer<typeof schema>>({
    resolver: zodResolver(schema),
    defaultValues: {
      // A sensible default the owner can change, rather than an empty field that
      // invites a wrong answer nobody notices until a slot is offered at 3am.
      timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
    },
  });

  const createBusiness = useMutation({
    mutationFn: (values: z.infer<typeof schema>) => api.createBusiness(accessToken, values),
    onSuccess: () => {
      setFailure(null);
      reset();
      void queryClient.invalidateQueries({ queryKey: ["businesses"] });
    },
    onError: (error) =>
      setFailure(
        error instanceof ApiError
          ? error.body.message
          : "Could not reach the server. Try again.",
      ),
  });

  return (
    <div className="flex flex-col gap-8">
      <section>
        <h1 className="text-2xl font-semibold">Your businesses</h1>

        {businesses.isPending ? (
          <p className="mt-4 text-slate-600">Loading your businesses…</p>
        ) : businesses.isError ? (
          <div className="mt-4">
            <FormError message="Could not load your businesses." />
            <button
              className="mt-2 text-sm underline"
              type="button"
              onClick={() => businesses.refetch()}
            >
              Try again
            </button>
          </div>
        ) : businesses.data.length === 0 ? (
          <p className="mt-4 rounded-md border border-dashed border-slate-300 p-6 text-slate-600">
            You have no businesses yet. Create one below and you will get a public booking
            link customers can use.
          </p>
        ) : (
          <ul className="mt-4 divide-y divide-slate-200 rounded-md border border-slate-200 bg-white">
            {businesses.data.map((business) => (
              <li key={business.id} className="px-4 py-3">
                <Link className="font-medium underline" href={`/dashboard/${business.id}`}>
                  {business.name}
                </Link>
                <p className="text-sm text-slate-600">
                  /book/{business.slug} · {business.timezone}
                </p>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section>
        <h2 className="text-lg font-semibold">Add a business</h2>
        <form
          className="mt-4 flex flex-col gap-4"
          onSubmit={handleSubmit((values) => createBusiness.mutate(values))}
        >
          <FormError message={failure} />

          <Field label="Business name" error={errors.name?.message}>
            <input className={inputClass} {...register("name")} />
          </Field>

          <Field label="Time zone" error={errors.timezone?.message}>
            <input className={inputClass} {...register("timezone")} />
          </Field>

          <button className={buttonClass} type="submit" disabled={createBusiness.isPending}>
            {createBusiness.isPending ? "Creating…" : "Create business"}
          </button>
        </form>
      </section>
    </div>
  );
}
