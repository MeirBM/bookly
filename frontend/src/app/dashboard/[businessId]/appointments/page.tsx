"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { use, useState } from "react";
import { AsyncSection } from "@/components/AsyncSection";
import { inputClass } from "@/components/Field";
import { FormError } from "@/components/FormError";
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
      <h1 className="text-2xl font-semibold">Appointments</h1>

      <form className="flex flex-wrap items-end gap-3">
        <label className="text-sm">
          <span className="block text-slate-700">From</span>
          <input
            className={inputClass}
            type="date"
            value={from}
            onChange={(event) => setFrom(event.target.value)}
          />
        </label>
        <label className="text-sm">
          <span className="block text-slate-700">To</span>
          <input
            className={inputClass}
            type="date"
            value={to}
            onChange={(event) => setTo(event.target.value)}
          />
        </label>
      </form>

      <FormError message={failure} />

      <AsyncSection
        query={appointments}
        label="appointments"
        isEmpty={(data) => data.length === 0}
        empty={
          <>
            No appointments in this range. Bookings made on your public page appear here as soon as
            they are taken.
          </>
        }
      >
        {(data) => (
          <ul
            className="divide-y divide-slate-200 rounded-md border border-slate-200 bg-white"
            data-testid="appointment-list"
          >
            {data.map((appointment) => (
              <li key={appointment.id} className="flex items-center justify-between px-4 py-3">
                <div>
                  <p className="font-medium">
                    {appointment.serviceName} · {appointment.customerName}
                  </p>
                  <p className="text-sm text-slate-600">
                    {zone ? format(appointment.startsAt) : appointment.startsAt} ·{" "}
                    {appointment.employeeName} ·{" "}
                    <span
                      className={
                        appointment.status === "CANCELLED" ? "text-slate-500" : "text-slate-700"
                      }
                    >
                      {appointment.status.toLowerCase()}
                    </span>
                  </p>
                </div>
                {appointment.status !== "CANCELLED" ? (
                  <button
                    className="text-sm underline"
                    type="button"
                    onClick={() => cancel.mutate(appointment.id)}
                  >
                    Cancel
                  </button>
                ) : null}
              </li>
            ))}
          </ul>
        )}
      </AsyncSection>
    </div>
  );
}
