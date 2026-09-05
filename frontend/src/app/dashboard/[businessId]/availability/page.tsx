"use client";

import { useQuery } from "@tanstack/react-query";
import { use, useState } from "react";
import { AsyncSection } from "@/components/AsyncSection";
import { inputClass } from "@/components/Field";
import { api } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";

/**
 * Criterion 2.22: the real slots from the engine, or a plain statement that there are none.
 *
 * <p>Times are rendered in the *business's* zone, which the response carries, not the viewer's. An
 * owner in one country checking a shop in another must see the shop's clock, and a slot shown in
 * the wrong zone is worse than no slot at all.
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
      <h1 className="text-2xl font-semibold">Availability</h1>

      {services.isPending ? (
        <p className="py-6 text-slate-600" role="status">
          Loading services…
        </p>
      ) : services.isError ? (
        // Without this the screen sat on "Loading availability…" for ever when the service list
        // failed: the availability query stays disabled with no service to ask about, so its own
        // error state never fires. A permanent loading state is the loading state wearing the
        // error's job, and a failure has to look like a failure.
        <div role="alert">
          <p className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-800">
            Could not load services, so availability cannot be worked out.
          </p>
          <button className="mt-2 text-sm underline" type="button" onClick={() => services.refetch()}>
            Try again
          </button>
        </div>
      ) : services.data.length === 0 ? (
        <p className="rounded-md border border-dashed border-slate-300 p-6 text-slate-600">
          Add a service before checking availability — how long a service takes is what decides
          which times can be offered.
        </p>
      ) : (
        <>
          <form className="flex flex-wrap items-end gap-3">
            <label className="text-sm">
              <span className="block text-slate-700">Service</span>
              <select
                className={inputClass}
                value={chosenService}
                onChange={(event) => setServiceId(event.target.value)}
              >
                {(services.data ?? []).map((service) => (
                  <option key={service.id} value={service.id}>
                    {service.name} ({service.durationMinutes} min)
                  </option>
                ))}
              </select>
            </label>

            <label className="text-sm">
              <span className="block text-slate-700">Employee</span>
              <select
                className={inputClass}
                value={employeeId}
                onChange={(event) => setEmployeeId(event.target.value)}
              >
                <option value="">Any available</option>
                {(employees.data ?? []).map((employee) => (
                  <option key={employee.id} value={employee.id}>
                    {employee.fullName}
                  </option>
                ))}
              </select>
            </label>

            <label className="text-sm">
              <span className="block text-slate-700">Date</span>
              <input
                className={inputClass}
                type="date"
                value={date}
                onChange={(event) => setDate(event.target.value)}
              />
            </label>
          </form>

          <AsyncSection
            query={availability}
            label="availability"
            isEmpty={(data) => data.slots.length === 0}
            empty={
              <>
                No free times on this date. That can mean nobody who performs this service works
                today, the day is fully booked or blocked, or the remaining gaps are too short for
                it.
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
                <div>
                  <p className="mb-3 text-sm text-slate-600">
                    {data.slots.length} slot{data.slots.length === 1 ? "" : "s"}, shown in{" "}
                    {data.timezone}.
                  </p>
                  <ul className="flex flex-wrap gap-2">
                    {data.slots.map((slot) => (
                      <li
                        key={slot.start}
                        className="rounded-md border border-slate-300 bg-white px-3 py-2 text-sm"
                        title={
                          employeeId
                            ? undefined
                            : slot.employeeIds.map(employeeName).join(", ")
                        }
                      >
                        <span className="font-medium">
                          {formatter.format(new Date(slot.start))}
                        </span>
                        {!employeeId && slot.employeeIds.length > 1 ? (
                          <span className="ml-2 text-slate-600">
                            {slot.employeeIds.length} people
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
