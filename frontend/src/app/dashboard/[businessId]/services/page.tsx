"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { use, useState } from "react";
import { AsyncSection } from "@/components/AsyncSection";
import { Field } from "@/components/Field";
import { FormError } from "@/components/FormError";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { Input } from "@/components/ui/Input";
import { PageHeader } from "@/components/ui/PageHeader";
import { ApiError, api } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";

export default function ServicesPage({ params }: { params: Promise<{ businessId: string }> }) {
  const { businessId } = use(params);
  const { tokens } = useAuth();
  const token = tokens?.accessToken ?? "";
  const queryClient = useQueryClient();
  const [failure, setFailure] = useState<string | null>(null);
  const [name, setName] = useState("");
  const [duration, setDuration] = useState("30");

  const services = useQuery({
    queryKey: ["services", businessId],
    queryFn: () => api.listServices(token, businessId),
    enabled: Boolean(token),
  });

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ["services", businessId] });
    void queryClient.invalidateQueries({ queryKey: ["setup", businessId] });
  };
  const onError = (error: unknown) =>
    setFailure(error instanceof ApiError ? error.body.message : "Could not reach the server.");

  const create = useMutation({
    mutationFn: () =>
      api.createService(token, businessId, {
        name: name.trim(),
        durationMinutes: Number(duration),
      }),
    onSuccess: () => {
      setFailure(null);
      setName("");
      invalidate();
    },
    onError,
  });

  const remove = useMutation({
    mutationFn: (serviceId: string) => api.deleteService(token, businessId, serviceId),
    onSuccess: invalidate,
    onError,
  });

  return (
    <div className="flex flex-col gap-8">
      <PageHeader
        title="Services"
        description="How long a service takes is what decides which times can be offered, so the duration matters more than it looks."
      />

      <FormError message={failure} />

      <AsyncSection
        query={services}
        label="services"
        isEmpty={(data) => data.length === 0}
        empty={
          <>
            No services yet. A service is what a customer books — a haircut, a session — and its
            length decides which slots can be offered.
          </>
        }
      >
        {(data) => (
          <ul className="flex flex-col gap-3">
            {data.map((service) => (
              <li key={service.id}>
                <Card className="flex items-center justify-between gap-4" padded={false}>
                  <div className="min-w-0 px-5 py-4">
                    <p className="truncate font-medium text-ink">{service.name}</p>
                    <p className="mt-0.5 text-sm text-ink-subtle">
                      {service.durationMinutes} minutes
                    </p>
                  </div>
                  <div className="px-5">
                    <Button
                      variant="danger"
                      size="sm"
                      type="button"
                      onClick={() => remove.mutate(service.id)}
                    >
                      Remove
                    </Button>
                  </div>
                </Card>
              </li>
            ))}
          </ul>
        )}
      </AsyncSection>

      <Card>
        <h2 className="text-lg font-semibold text-ink">Add a service</h2>
        <form
          className="mt-4 flex flex-col gap-4 sm:max-w-md"
          onSubmit={(event) => {
            event.preventDefault();
            if (name.trim()) {
              create.mutate();
            }
          }}
        >
          <Field label="Name">
            <Input value={name} onChange={(event) => setName(event.target.value)} required />
          </Field>
          <Field label="Duration in minutes" hint="Whole minutes, and at least one.">
            <Input
              type="number"
              min={1}
              max={1440}
              value={duration}
              onChange={(event) => setDuration(event.target.value)}
              required
            />
          </Field>
          <Button type="submit" loading={create.isPending} disabled={!name.trim()}>
            Add service
          </Button>
        </form>
      </Card>
    </div>
  );
}
