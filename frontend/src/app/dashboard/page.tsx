"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";
import { AsyncSection } from "@/components/AsyncSection";
import { Field } from "@/components/Field";
import { FormError } from "@/components/FormError";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { Input } from "@/components/ui/Input";
import { PageHeader } from "@/components/ui/PageHeader";
import { ApiError, api } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";

export default function DashboardPage() {
  const { tokens } = useAuth();
  const token = tokens?.accessToken ?? "";
  const queryClient = useQueryClient();
  const [failure, setFailure] = useState<string | null>(null);
  const [name, setName] = useState("");
  const [timezone, setTimezone] = useState(
    () => Intl.DateTimeFormat().resolvedOptions().timeZone,
  );

  const businesses = useQuery({
    queryKey: ["businesses"],
    queryFn: () => api.listBusinesses(token),
    enabled: Boolean(token),
  });

  const create = useMutation({
    mutationFn: () => api.createBusiness(token, { name: name.trim(), timezone }),
    onSuccess: () => {
      setFailure(null);
      setName("");
      void queryClient.invalidateQueries({ queryKey: ["businesses"] });
    },
    onError: (error) =>
      setFailure(error instanceof ApiError ? error.body.message : "Could not reach the server."),
  });

  return (
    <div className="flex flex-col gap-8">
      <PageHeader
        title="Your businesses"
        description="Each business has its own services, people, hours and public booking page."
      />

      <AsyncSection
        query={businesses}
        label="your businesses"
        isEmpty={(data) => data.length === 0}
        empty={
          <>
            No businesses yet. Create one below and you will get a public booking link customers can
            use straight away.
          </>
        }
      >
        {(data) => (
          <ul className="grid gap-3 sm:grid-cols-2">
            {data.map((business) => (
              <li key={business.id}>
                <Link
                  href={`/dashboard/${business.id}`}
                  className="group block rounded-lg border border-border bg-surface p-5 shadow-card transition-shadow duration-150 hover:shadow-card-hover"
                >
                  <p className="font-medium text-ink group-hover:text-brand-700">{business.name}</p>
                  <p className="mt-1 truncate text-sm text-ink-subtle">
                    /book/{business.slug} · {business.timezone}
                  </p>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </AsyncSection>

      <Card>
        <h2 className="text-lg font-semibold text-ink">Add a business</h2>
        <form
          className="mt-4 flex flex-col gap-4 sm:max-w-md"
          onSubmit={(event) => {
            event.preventDefault();
            if (name.trim()) {
              create.mutate();
            }
          }}
        >
          <FormError message={failure} />
          <Field label="Business name">
            <Input value={name} onChange={(event) => setName(event.target.value)} required />
          </Field>
          <Field
            label="Time zone"
            hint="Every time your customers see is shown on this clock."
          >
            <Input value={timezone} onChange={(event) => setTimezone(event.target.value)} required />
          </Field>
          <Button type="submit" loading={create.isPending} disabled={!name.trim()}>
            Create business
          </Button>
        </form>
      </Card>
    </div>
  );
}
