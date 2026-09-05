"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { use, useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { AsyncSection } from "@/components/AsyncSection";
import { Field, buttonClass, inputClass } from "@/components/Field";
import { FormError } from "@/components/FormError";
import { ApiError, api } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { zodResolver } from "@/lib/zod-resolver";

const schema = z.object({
  name: z.string().min(1, "Give the service a name.").max(120),
  // Mirrors the server's bounds. The server still enforces them; this saves a round trip.
  durationMinutes: z.coerce
    .number()
    .int("Use whole minutes.")
    .min(1, "A service must take at least a minute.")
    .max(1440, "Use 1440 minutes or fewer."),
});

export default function ServicesPage({ params }: { params: Promise<{ businessId: string }> }) {
  const { businessId } = use(params);
  const { tokens } = useAuth();
  const token = tokens?.accessToken ?? "";
  const queryClient = useQueryClient();
  const [failure, setFailure] = useState<string | null>(null);

  const services = useQuery({
    queryKey: ["services", businessId],
    queryFn: () => api.listServices(token, businessId),
    enabled: Boolean(token),
  });

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ["services", businessId] });
    void queryClient.invalidateQueries({ queryKey: ["setup", businessId] });
  };

  const { register, handleSubmit, reset, formState } = useForm<z.infer<typeof schema>>({
    resolver: zodResolver(schema),
    defaultValues: { name: "", durationMinutes: 30 },
  });

  const create = useMutation({
    mutationFn: (values: z.infer<typeof schema>) => api.createService(token, businessId, values),
    onSuccess: () => {
      setFailure(null);
      reset();
      invalidate();
    },
    onError: (error) =>
      setFailure(error instanceof ApiError ? error.body.message : "Could not reach the server."),
  });

  const remove = useMutation({
    mutationFn: (serviceId: string) => api.deleteService(token, businessId, serviceId),
    onSuccess: invalidate,
    onError: (error) =>
      setFailure(error instanceof ApiError ? error.body.message : "Could not reach the server."),
  });

  return (
    <div className="flex flex-col gap-8">
      <section>
        <h1 className="text-2xl font-semibold">Services</h1>
        <div className="mt-4">
          <AsyncSection
            query={services}
            label="services"
            isEmpty={(data) => data.length === 0}
            empty={
              <>
                No services yet. A service is what a customer books — a haircut, a session — and how
                long it takes decides which slots can be offered.
              </>
            }
          >
            {(data) => (
              <ul className="divide-y divide-slate-200 rounded-md border border-slate-200 bg-white">
                {data.map((service) => (
                  <li key={service.id} className="flex items-center justify-between px-4 py-3">
                    <span>
                      <span className="font-medium">{service.name}</span>
                      <span className="ml-2 text-sm text-slate-600">
                        {service.durationMinutes} min
                      </span>
                    </span>
                    <button
                      className="text-sm underline"
                      type="button"
                      onClick={() => remove.mutate(service.id)}
                    >
                      Remove
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </AsyncSection>
        </div>
      </section>

      <section>
        <h2 className="text-lg font-semibold">Add a service</h2>
        <form
          className="mt-4 flex flex-col gap-4"
          onSubmit={handleSubmit((values) => create.mutate(values))}
        >
          <FormError message={failure} />
          <Field label="Name" error={formState.errors.name?.message}>
            <input className={inputClass} {...register("name")} />
          </Field>
          <Field label="Duration in minutes" error={formState.errors.durationMinutes?.message}>
            <input className={inputClass} type="number" min={1} {...register("durationMinutes")} />
          </Field>
          <button className={buttonClass} type="submit" disabled={create.isPending}>
            {create.isPending ? "Adding…" : "Add service"}
          </button>
        </form>
      </section>
    </div>
  );
}
