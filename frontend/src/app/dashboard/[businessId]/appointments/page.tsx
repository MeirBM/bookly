"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { use, useState } from "react";
import { AsyncSection } from "@/components/AsyncSection";
import { FormError } from "@/components/FormError";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { Input } from "@/components/ui/Input";
import { PageHeader } from "@/components/ui/PageHeader";
import { ApiError, api } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { useBusiness } from "@/lib/use-business";

function isoDate(offsetDays: number) {
  const date = new Date();
  date.setDate(date.getDate() + offsetDays);
  return date.toISOString().slice(0, 10);
}

export default function AppointmentsPage({
  params,
}: {
  params: Promise<{ businessId: string }>;
}) {
  const { businessId } = use(params);
  const { tokens } = useAuth();
  const token = tokens?.accessToken ?? "";
  const queryClient = useQueryClient();
  const business = useBusiness(businessId);
  const [from, setFrom] = useState(() => isoDate(-7));
  const [to, setTo] = useState(() => isoDate(30));
  const [failure, setFailure] = useState<string | null>(null);

  const appointments = useQuery({
    queryKey: ["appointments", businessId, from, to],
    queryFn: () => api.listAppointments(token, businessId, from, to),
    enabled: Boolean(token && from && to),
  });

  const cancel = useMutation({
    mutationFn: (appointmentId: string) =>
      api.cancelAppointment(token, businessId, appointmentId),
    onSuccess: () => {
      setFailure(null);
      void queryClient.invalidateQueries({ queryKey: ["appointments", businessId] });
      void queryClient.invalidateQueries({ queryKey: ["calendar", businessId] });
    },
    onError: (error) =>
      setFailure(error instanceof ApiError ? error.body.message : "Could not reach the server."),
  });

  const zone = business.data?.timezone;
  const format = (value: string) =>
    new Intl.DateTimeFormat("en-GB", {
      dateStyle: "medium",
      timeStyle: "short",
      timeZone: zone,
    }).format(new Date(value));

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Appointments"
        description={zone ? `Times shown in ${zone}.` : undefined}
      />

      <Card className="flex flex-wrap items-end gap-4">
        <label className="text-sm">
          <span className="mb-1.5 block font-medium text-ink">From</span>
          <Input
            className="w-44"
            type="date"
            value={from}
            onChange={(event) => setFrom(event.target.value)}
          />
        </label>
        <label className="text-sm">
          <span className="mb-1.5 block font-medium text-ink">To</span>
          <Input
            className="w-44"
            type="date"
            value={to}
            onChange={(event) => setTo(event.target.value)}
          />
        </label>
      </Card>

      <FormError message={failure} />

      <AsyncSection
        query={appointments}
        label="appointments"
        isEmpty={(data) => data.length === 0}
        empty={
          <>
            No appointments in this range. Bookings made on your public page appear here the moment
            they are taken.
          </>
        }
      >
        {(data) => (
          <ul
            className="flex flex-col gap-3"
            data-testid="appointment-list"
          >
            {data.map((appointment) => {
              const cancelled = appointment.status === "CANCELLED";
              return (
                <li key={appointment.id}>
                  <Card className="flex flex-wrap items-center justify-between gap-4">
                    <div className="min-w-0">
                      <div className="flex flex-wrap items-center gap-2">
                        <p className={`font-medium ${cancelled ? "text-ink-subtle line-through" : "text-ink"}`}>
                          {appointment.customerName}
                        </p>
                        <Badge tone={cancelled ? "neutral" : "success"}>
                          {appointment.status.toLowerCase()}
                        </Badge>
                      </div>
                      <p className="mt-1 text-sm text-ink-muted">
                        {appointment.serviceName} with {appointment.employeeName}
                      </p>
                      <p className="mt-0.5 text-sm tabular-nums text-ink-subtle">
                        {zone ? format(appointment.startsAt) : appointment.startsAt}
                      </p>
                    </div>
                    {cancelled ? null : (
                      <Button
                        variant="danger"
                        size="sm"
                        type="button"
                        loading={cancel.isPending && cancel.variables === appointment.id}
                        onClick={() => cancel.mutate(appointment.id)}
                      >
                        Cancel
                      </Button>
                    )}
                  </Card>
                </li>
              );
            })}
          </ul>
        )}
      </AsyncSection>
    </div>
  );
}
