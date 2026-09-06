"use client";

import { useQuery } from "@tanstack/react-query";
import { use, useState } from "react";
import { AsyncSection } from "@/components/AsyncSection";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { Input, Select } from "@/components/ui/Input";
import { PageHeader } from "@/components/ui/PageHeader";
import { api } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";

/**
 * What the engine would offer a customer right now.
 *
 * <p>Times render in the *business's* zone, which the response carries, not the viewer's: an owner
 * checking a shop in another country must see the shop's clock, and a slot shown in the wrong zone
 * is worse than no slot at all.
 */
export default function AvailabilityPage({
  params,
}: {
  params: Promise<{ businessId: string }>;
}) {
  const { businessId } = use(params);
  const { tokens } = useAuth();
  const token = tokens?.accessToken ?? "";

  const [serviceId, setServiceId] = useState("");
  const [employeeId, setEmployeeId] = useState("");
  const [date, setDate] = useState(() => new Date().toISOString().slice(0, 10));

  const services = useQuery({
    queryKey: ["services", businessId],
    queryFn: () => api.listServices(token, businessId),
    enabled: Boolean(token),
  });
  const employees = useQuery({
    queryKey: ["employees", businessId],
    queryFn: () => api.listEmployees(token, businessId),
    enabled: Boolean(token),
  });

  const chosenService = serviceId || services.data?.[0]?.id || "";

  const availability = useQuery({
    queryKey: ["availability", businessId, chosenService, employeeId, date],
    queryFn: () =>
      api.availability(token, businessId, {
        serviceId: chosenService,
        employeeId: employeeId || undefined,
        date,
      }),
    enabled: Boolean(token && chosenService && date),
  });

  const employeeName = (id: string) =>
    employees.data?.find((employee) => employee.id === id)?.fullName ?? "someone";

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Availability"
        description="What a customer would be offered right now, worked out from hours, bookings and blocked time."
      />

      {services.isPending ? (
        <p role="status" className="text-sm text-ink-subtle">
          Loading services…
        </p>
      ) : services.isError ? (
        <div role="alert" className="flex flex-col items-start gap-3">
          <p className="rounded-md border border-danger-200 bg-danger-50 px-3.5 py-2.5 text-sm text-danger-700">
            Could not load services, so availability cannot be worked out.
          </p>
          <Button variant="secondary" size="sm" type="button" onClick={() => services.refetch()}>
            Try again
          </Button>
        </div>
      ) : services.data.length === 0 ? (
        <div className="rounded-lg border border-dashed border-border-strong bg-surface px-6 py-10 text-center text-ink-muted">
          Add a service before checking availability — how long a service takes is what decides
          which times can be offered.
        </div>
      ) : (
        <>
          <Card className="grid gap-4 sm:grid-cols-3">
            <label className="text-sm">
              <span className="mb-1.5 block font-medium text-ink">Service</span>
              <Select value={chosenService} onChange={(event) => setServiceId(event.target.value)}>
                {services.data.map((service) => (
                  <option key={service.id} value={service.id}>
                    {service.name} ({service.durationMinutes} min)
                  </option>
                ))}
              </Select>
            </label>
            <label className="text-sm">
              <span className="mb-1.5 block font-medium text-ink">Employee</span>
              <Select value={employeeId} onChange={(event) => setEmployeeId(event.target.value)}>
                <option value="">Any available</option>
                {(employees.data ?? []).map((employee) => (
                  <option key={employee.id} value={employee.id}>
                    {employee.fullName}
                  </option>
                ))}
              </Select>
            </label>
            <label className="text-sm">
              <span className="mb-1.5 block font-medium text-ink">Date</span>
              <Input type="date" value={date} onChange={(event) => setDate(event.target.value)} />
            </label>
          </Card>

          <AsyncSection
            query={availability}
            label="availability"
            skeletonRows={2}
            isEmpty={(data) => data.slots.length === 0}
            empty={
              <>
                No free times on this date. Nobody who performs this service works today, the day is
                fully booked or blocked, or the remaining gaps are too short for it.
              </>
            }
          >
            {(data) => {
              const formatter = new Intl.DateTimeFormat("en-GB", {
                hour: "2-digit",
                minute: "2-digit",
                timeZone: data.timezone,
              });
              return (
                <div className="flex flex-col gap-3">
                  <p className="text-sm text-ink-muted">
                    {data.slots.length} slot{data.slots.length === 1 ? "" : "s"} · every{" "}
                    {data.stepMinutes} minutes · shown in {data.timezone}
                  </p>
                  <ul className="grid grid-cols-3 gap-2 sm:grid-cols-6">
                    {data.slots.map((slot) => (
                      <li
                        key={slot.start}
                        title={employeeId ? undefined : slot.employeeIds.map(employeeName).join(", ")}
                        className="flex h-11 items-center justify-center rounded-md border border-border bg-surface text-sm font-medium tabular-nums text-ink"
                      >
                        {formatter.format(new Date(slot.start))}
                        {!employeeId && slot.employeeIds.length > 1 ? (
                          <span className="ml-1.5 text-xs font-normal text-ink-subtle">
                            ×{slot.employeeIds.length}
                          </span>
                        ) : null}
                      </li>
                    ))}
                  </ul>
                </div>
              );
            }}
          </AsyncSection>
        </>
      )}
    </div>
  );
}
