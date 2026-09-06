"use client";

import { useQuery } from "@tanstack/react-query";
import { use, useState } from "react";
import { AsyncSection } from "@/components/AsyncSection";
import { api, type Appointment } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { useBusiness } from "@/lib/use-business";

/** Monday of the week containing the given date, in the viewer's own calendar terms. */
function startOfWeek(date: Date) {
  const copy = new Date(date);
  const weekday = (copy.getDay() + 6) % 7; // Monday = 0
  copy.setDate(copy.getDate() - weekday);
  copy.setHours(0, 0, 0, 0);
  return copy;
}

function isoDate(date: Date) {
  return date.toISOString().slice(0, 10);
}

/**
 * A week at a glance.
 *
 * <p>Days are worked out in the **business's** zone, not the viewer's. An appointment at 00:30 in
 * Jerusalem is Tuesday there and Monday in London, and putting it in the wrong column would be a
 * calendar that quietly lies about which day someone is coming in.
 */
export default function CalendarPage({ params }: { params: Promise<{ businessId: string }> }) {
  const { businessId } = use(params);
  const { tokens } = useAuth();
  const token = tokens?.accessToken ?? "";
  const business = useBusiness(businessId);
  const [weekStart, setWeekStart] = useState(() => startOfWeek(new Date()));

  const days = Array.from({ length: 7 }, (_, index) => {
    const day = new Date(weekStart);
    day.setDate(day.getDate() + index);
    return day;
  });
  const from = isoDate(days[0]);
  const to = isoDate(days[6]);

  const appointments = useQuery({
    queryKey: ["calendar", businessId, from, to],
    queryFn: () => api.listAppointments(token, businessId, from, to),
    enabled: Boolean(token),
  });

  const zone = business.data?.timezone;

  /** The calendar day an instant falls on, in the business's zone. */
  const dayKey = (instant: string) =>
    zone
      ? new Intl.DateTimeFormat("en-CA", { timeZone: zone }).format(new Date(instant))
      : instant.slice(0, 10);

  const timeOf = (instant: string) =>
    zone
      ? new Intl.DateTimeFormat("en-GB", {
          hour: "2-digit",
          minute: "2-digit",
          timeZone: zone,
        }).format(new Date(instant))
      : instant.slice(11, 16);

  const shift = (weeks: number) => {
    const next = new Date(weekStart);
    next.setDate(next.getDate() + weeks * 7);
    setWeekStart(next);
  };

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Calendar</h1>
        <div className="flex items-center gap-2 text-sm">
          <button className="underline" type="button" onClick={() => shift(-1)}>
            Previous week
          </button>
          <button className="underline" type="button" onClick={() => setWeekStart(startOfWeek(new Date()))}>
            This week
          </button>
          <button className="underline" type="button" onClick={() => shift(1)}>
            Next week
          </button>
        </div>
      </div>

      {zone ? <p className="text-sm text-slate-600">Times shown in {zone}.</p> : null}

      <AsyncSection
        query={appointments}
        label="the calendar"
        // Never "empty": a week with no bookings is still a week, and seven labelled days say
        // that plainly where a blank page would read as a screen that failed to load.
        isEmpty={() => false}
        empty={null}
      >
        {(data) => {
          const byDay = new Map<string, Appointment[]>();
          for (const appointment of data) {
            if (appointment.status === "CANCELLED") {
              continue;
            }
            const key = dayKey(appointment.startsAt);
            byDay.set(key, [...(byDay.get(key) ?? []), appointment]);
          }
          const total = [...byDay.values()].reduce((sum, list) => sum + list.length, 0);

          return (
            <>
              {total === 0 ? (
                <p className="text-slate-600" data-testid="empty-week">
                  No appointments this week.
                </p>
              ) : null}
              <div className="grid grid-cols-1 gap-2 sm:grid-cols-7" data-testid="calendar-week">
                {days.map((day) => {
                  const key = isoDate(day);
                  const forDay = (byDay.get(key) ?? []).sort((a, b) =>
                    a.startsAt.localeCompare(b.startsAt),
                  );
                  return (
                    <section
                      key={key}
                      data-testid={`day-${key}`}
                      className="rounded-md border border-slate-200 bg-white p-2"
                    >
                      <h2 className="text-xs font-medium text-slate-700">
                        {new Intl.DateTimeFormat("en-GB", {
                          weekday: "short",
                          day: "numeric",
                          month: "short",
                        }).format(day)}
                      </h2>
                      {forDay.length === 0 ? (
                        <p className="mt-2 text-xs text-slate-400">—</p>
                      ) : (
                        <ul className="mt-2 flex flex-col gap-1">
                          {forDay.map((appointment) => (
                            <li
                              key={appointment.id}
                              className="rounded bg-slate-900 px-2 py-1 text-xs text-white"
                            >
                              <span className="font-medium">{timeOf(appointment.startsAt)}</span>{" "}
                              {appointment.serviceName}
                              <span className="block text-slate-300">
                                {appointment.customerName}
                              </span>
                            </li>
                          ))}
                        </ul>
                      )}
                    </section>
                  );
                })}
              </div>
            </>
          );
        }}
      </AsyncSection>
    </div>
  );
}
