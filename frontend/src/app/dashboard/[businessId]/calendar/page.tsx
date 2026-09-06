"use client";

import { useQuery } from "@tanstack/react-query";
import { use, useState } from "react";
import { AsyncSection } from "@/components/AsyncSection";
import { WeekDay } from "@/components/calendar/WeekDay";
import { Button } from "@/components/ui/Button";
import { PageHeader } from "@/components/ui/PageHeader";
import { api, type Appointment } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { useBusiness } from "@/lib/use-business";

/** Monday of the week containing the given date. */
function startOfWeek(date: Date) {
  const copy = new Date(date);
  const weekday = (copy.getDay() + 6) % 7;
  copy.setDate(copy.getDate() - weekday);
  copy.setHours(0, 0, 0, 0);
  return copy;
}

const isoDate = (date: Date) => date.toISOString().slice(0, 10);

/**
 * A week at a glance.
 *
 * <p>Days are worked out in the **business's** zone, not the viewer's. An appointment at 00:30 in
 * Jerusalem is Tuesday there and Monday in London, and putting it in the wrong column is a calendar
 * quietly lying about which day someone is coming in.
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
      ? new Intl.DateTimeFormat("en-GB", { hour: "2-digit", minute: "2-digit", timeZone: zone })
          .format(new Date(instant))
      : instant.slice(11, 16);

  const shift = (weeks: number) => {
    const next = new Date(weekStart);
    next.setDate(next.getDate() + weeks * 7);
    setWeekStart(next);
  };

  const today = isoDate(new Date());
  const weekLabel = `${new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "long" }).format(days[0])} – ${new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "long", year: "numeric" }).format(days[6])}`;

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Calendar"
        description={zone ? `${weekLabel} · times shown in ${zone}.` : weekLabel}
        action={
          <div className="flex items-center gap-2">
            <Button variant="secondary" size="sm" type="button" onClick={() => shift(-1)}>
              Previous week
            </Button>
            <Button
              variant="ghost"
              size="sm"
              type="button"
              onClick={() => setWeekStart(startOfWeek(new Date()))}
            >
              This week
            </Button>
            <Button variant="secondary" size="sm" type="button" onClick={() => shift(1)}>
              Next week
            </Button>
          </div>
        }
      />

      <AsyncSection
        query={appointments}
        label="the calendar"
        skeletonRows={4}
        // Never "empty": a week with no bookings is still a week, and seven labelled days say so
        // where a blank page would read as a screen that failed to load.
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
            <div className="flex flex-col gap-4">
              {total === 0 ? (
                <p className="text-sm text-ink-muted" data-testid="empty-week">
                  No appointments this week.
                </p>
              ) : (
                <p className="text-sm text-ink-muted">
                  {total} appointment{total === 1 ? "" : "s"} this week.
                </p>
              )}

              <div
                className="grid grid-cols-1 gap-2 sm:grid-cols-2 lg:grid-cols-7"
                data-testid="calendar-week"
              >
                {days.map((day) => {
                  const key = isoDate(day);
                  return (
                    <WeekDay
                      key={key}
                      label={new Intl.DateTimeFormat("en-GB", {
                        weekday: "short",
                        day: "numeric",
                        month: "short",
                      }).format(day)}
                      isToday={key === today}
                      appointments={(byDay.get(key) ?? []).sort((a, b) =>
                        a.startsAt.localeCompare(b.startsAt),
                      )}
                      timeOf={timeOf}
                    />
                  );
                })}
              </div>
            </div>
          );
        }}
      </AsyncSection>
    </div>
  );
}
