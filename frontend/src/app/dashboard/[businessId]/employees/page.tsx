"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { use, useState } from "react";
import { AsyncSection } from "@/components/AsyncSection";
import { buttonClass, inputClass } from "@/components/Field";
import { FormError } from "@/components/FormError";
import { ApiError, api, type Weekday } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";

const WEEKDAYS: Weekday[] = [
  "MONDAY",
  "TUESDAY",
  "WEDNESDAY",
  "THURSDAY",
  "FRIDAY",
  "SATURDAY",
  "SUNDAY",
];

const title = (day: string) => day.charAt(0) + day.slice(1).toLowerCase();

export default function EmployeesPage({ params }: { params: Promise<{ businessId: string }> }) {
  const { businessId } = use(params);
  const { tokens } = useAuth();
  const token = tokens?.accessToken ?? "";
  const queryClient = useQueryClient();
  const [failure, setFailure] = useState<string | null>(null);
  const [name, setName] = useState("");
  const [openEmployee, setOpenEmployee] = useState<string | null>(null);

  const employees = useQuery({
    queryKey: ["employees", businessId],
    queryFn: () => api.listEmployees(token, businessId),
    enabled: Boolean(token),
  });
  const services = useQuery({
    queryKey: ["services", businessId],
    queryFn: () => api.listServices(token, businessId),
    enabled: Boolean(token),
  });

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ["employees", businessId] });
    void queryClient.invalidateQueries({ queryKey: ["setup", businessId] });
  };
  const onError = (error: unknown) =>
    setFailure(error instanceof ApiError ? error.body.message : "Could not reach the server.");

  const create = useMutation({
    mutationFn: () => api.createEmployee(token, businessId, { fullName: name.trim() }),
    onSuccess: () => {
      setFailure(null);
      setName("");
      invalidate();
    },
    onError,
  });

  const toggleService = useMutation({
    mutationFn: ({ employeeId, serviceIds }: { employeeId: string; serviceIds: string[] }) =>
      api.setEmployeeServices(token, businessId, employeeId, serviceIds),
    onSuccess: invalidate,
    onError,
  });

  return (
    <div className="flex flex-col gap-8">
      <section>
        <h1 className="text-2xl font-semibold">Employees</h1>
        <div className="mt-4">
          <AsyncSection
            query={employees}
            label="employees"
            isEmpty={(data) => data.length === 0}
            empty={
              <>
                No employees yet. Availability is worked out per person, so nothing can be offered
                until at least one person exists and has hours.
              </>
            }
          >
            {(data) => (
              <ul className="divide-y divide-slate-200 rounded-md border border-slate-200 bg-white">
                {data.map((employee) => (
                  <li key={employee.id} className="px-4 py-3">
                    <div className="flex items-center justify-between">
                      <span className="font-medium">{employee.fullName}</span>
                      <button
                        className="text-sm underline"
                        type="button"
                        onClick={() =>
                          setOpenEmployee(openEmployee === employee.id ? null : employee.id)
                        }
                      >
                        {openEmployee === employee.id ? "Hide" : "Services and hours"}
                      </button>
                    </div>

                    {openEmployee === employee.id ? (
                      <div className="mt-3 flex flex-col gap-4 border-t border-slate-100 pt-3">
                        <div>
                          <p className="text-sm font-medium text-slate-700">Performs</p>
                          {services.data && services.data.length > 0 ? (
                            <div className="mt-2 flex flex-wrap gap-2">
                              {services.data.map((service) => {
                                const on = employee.serviceIds.includes(service.id);
                                return (
                                  <button
                                    key={service.id}
                                    type="button"
                                    aria-pressed={on}
                                    className={`rounded-full border px-3 py-1 text-sm ${
                                      on
                                        ? "border-slate-900 bg-slate-900 text-white"
                                        : "border-slate-300 text-slate-700"
                                    }`}
                                    onClick={() =>
                                      toggleService.mutate({
                                        employeeId: employee.id,
                                        serviceIds: on
                                          ? employee.serviceIds.filter((id) => id !== service.id)
                                          : [...employee.serviceIds, service.id],
                                      })
                                    }
                                  >
                                    {service.name}
                                  </button>
                                );
                              })}
                            </div>
                          ) : (
                            <p className="mt-1 text-sm text-slate-600">
                              Add a service first — an employee linked to nothing is offered for
                              nothing.
                            </p>
                          )}
                        </div>
                        <WorkingHoursEditor
                          businessId={businessId}
                          employeeId={employee.id}
                          token={token}
                          onFailure={onError}
                        />
                      </div>
                    ) : null}
                  </li>
                ))}
              </ul>
            )}
          </AsyncSection>
        </div>
      </section>

      <section>
        <h2 className="text-lg font-semibold">Add an employee</h2>
        <FormError message={failure} />
        <form
          className="mt-3 flex gap-2"
          onSubmit={(event) => {
            event.preventDefault();
            if (name.trim()) {
              create.mutate();
            }
          }}
        >
          <input
            className={inputClass}
            aria-label="Employee name"
            value={name}
            onChange={(event) => setName(event.target.value)}
          />
          <button className={`${buttonClass} w-auto`} type="submit" disabled={create.isPending}>
            Add
          </button>
        </form>
      </section>
    </div>
  );
}

/** Two windows on one weekday are how a break is expressed — the UI says so rather than hiding it. */
function WorkingHoursEditor({
  businessId,
  employeeId,
  token,
  onFailure,
}: {
  businessId: string;
  employeeId: string;
  token: string;
  onFailure: (error: unknown) => void;
}) {
  const queryClient = useQueryClient();
  const [weekday, setWeekday] = useState<Weekday>("MONDAY");
  const [startsAt, setStartsAt] = useState("09:00");
  const [endsAt, setEndsAt] = useState("17:00");

  const hours = useQuery({
    queryKey: ["working-hours", businessId, employeeId],
    queryFn: () => api.listWorkingHours(token, businessId, employeeId),
    enabled: Boolean(token),
  });

  const invalidate = () =>
    void queryClient.invalidateQueries({ queryKey: ["working-hours", businessId, employeeId] });

  const add = useMutation({
    mutationFn: () =>
      api.addWorkingHours(token, businessId, employeeId, { weekday, startsAt, endsAt }),
    onSuccess: invalidate,
    onError: onFailure,
  });
  const remove = useMutation({
    mutationFn: (id: string) => api.deleteWorkingHours(token, businessId, id),
    onSuccess: invalidate,
    onError: onFailure,
  });

  return (
    <div>
      <p className="text-sm font-medium text-slate-700">Working hours</p>
      <div className="mt-2">
        <AsyncSection
          query={hours}
          label="working hours"
          isEmpty={(data) => data.length === 0}
          empty={
            <span className="text-sm">
              No hours set, so this person is never offered. Add two windows on one day to leave a
              break between them.
            </span>
          }
        >
          {(data) => (
            <ul className="flex flex-col gap-1 text-sm">
              {data.map((window) => (
                <li key={window.id} className="flex items-center gap-2">
                  <span className="w-24 text-slate-700">{title(window.weekday)}</span>
                  <span className="text-slate-600">
                    {window.startsAt.slice(0, 5)}–{window.endsAt.slice(0, 5)}
                  </span>
                  <button
                    className="underline"
                    type="button"
                    onClick={() => remove.mutate(window.id)}
                  >
                    Remove
                  </button>
                </li>
              ))}
            </ul>
          )}
        </AsyncSection>
      </div>

      <form
        className="mt-3 flex flex-wrap items-end gap-2"
        onSubmit={(event) => {
          event.preventDefault();
          add.mutate();
        }}
      >
        <label className="text-sm">
          <span className="block text-slate-700">Day</span>
          <select
            className={inputClass}
            value={weekday}
            onChange={(event) => setWeekday(event.target.value as Weekday)}
          >
            {WEEKDAYS.map((day) => (
              <option key={day} value={day}>
                {title(day)}
              </option>
            ))}
          </select>
        </label>
        <label className="text-sm">
          <span className="block text-slate-700">From</span>
          <input
            className={inputClass}
            type="time"
            value={startsAt}
            onChange={(event) => setStartsAt(event.target.value)}
          />
        </label>
        <label className="text-sm">
          <span className="block text-slate-700">To</span>
          <input
            className={inputClass}
            type="time"
            value={endsAt}
            onChange={(event) => setEndsAt(event.target.value)}
          />
        </label>
        <button className={`${buttonClass} w-auto`} type="submit" disabled={add.isPending}>
          Add window
        </button>
      </form>
    </div>
  );
}
