"use client";

import { useQuery } from "@tanstack/react-query";
import { useEffect, useRef } from "react";
import { useToast } from "@/components/ui/Toast";
import { api, type Appointment } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { useBusiness } from "@/lib/use-business";

const POLL_MS = 20_000;
/** Far enough ahead to catch any booking a customer can actually make, short enough
 *  to stay one small response. The backend refuses ranges beyond 62 days. */
const WINDOW_DAYS = 45;

/**
 * A calendar date in the business's own zone, offset by whole days.
 *
 * <p>This read `new Date().toISOString().slice(0, 10)` until a review caught it, which is the
 * server-agnostic-looking mistake CLAUDE.md's Time section forbids: it is neither the viewer's zone
 * nor the business's, but UTC. An owner in Los Angeles opening the dashboard at 20:00 was already
 * on tomorrow's UTC date, so a customer booking for 21:30 that evening fell outside the window,
 * never appeared in the response, and raised no toast — every evening, which is precisely the miss
 * criterion 4.6 exists to prevent.
 *
 * <p>`en-CA` is not decoration: it is the locale that formats a date as `YYYY-MM-DD`, which is what
 * the endpoint takes. Falling back to UTC while the zone is still loading is safe because the
 * window also starts a day early.
 */
function isoDateIn(zone: string | undefined, offsetDays: number) {
  const date = new Date();
  date.setDate(date.getDate() + offsetDays);
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: zone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(date);
}

/**
 * Polls the owner's own appointment list and raises a toast for bookings that were not
 * there a poll ago.
 *
 * Deliberately its own query key rather than sharing ["appointments", …]: that key carries
 * the date filters the owner picks on the appointments screen, so sharing it would both tie
 * the alert window to whatever range they happened to select and drag that screen into a
 * 20-second refetch it never asked for.
 *
 * This is the query layer's own polling, not a second timer beside it. Nothing here needs
 * cleaning up by hand — when the dashboard unmounts the query unmounts with it and the
 * interval goes away. Background refetching stays off (the TanStack default), so a
 * dashboard left open in a hidden tab stops asking until someone looks at it again.
 */
export function useBookingAlerts(businessId: string) {
  const { tokens } = useAuth();
  const token = tokens?.accessToken ?? "";
  const business = useBusiness(businessId);
  const { show } = useToast();

  // Starting yesterday rather than today costs one day of a bounded query and removes the whole
  // class of off-by-one-day failure: whatever the zone does at a boundary, tonight is inside it.
  const zone = business.data?.timezone;
  const from = isoDateIn(zone, -1);
  const to = isoDateIn(zone, WINDOW_DAYS);

  const { data } = useQuery({
    queryKey: ["booking-alerts", businessId, from, to],
    queryFn: () => api.listAppointments(token, businessId, from, to),
    enabled: Boolean(token),
    refetchInterval: POLL_MS,
  });

  // null means "we have not seen a first response yet". The first response is the baseline:
  // everything in it was already booked before the owner opened the dashboard, and none of
  // it is news. Only what appears after that baseline raises anything.
  const seen = useRef<Set<string> | null>(null);
  const seenFor = useRef(businessId);

  useEffect(() => {
    if (seenFor.current !== businessId) {
      seenFor.current = businessId;
      seen.current = null;
    }
  }, [businessId]);

  useEffect(() => {
    if (!data) return;

    if (seen.current === null) {
      seen.current = new Set(data.map((appointment) => appointment.id));
      return;
    }

    const known = seen.current;
    const fresh = data.filter(
      (appointment) => !known.has(appointment.id) && appointment.status !== "CANCELLED",
    );

    // Record everything, including the cancelled rows that raise nothing, so an appointment
    // cancelled and later reinstated does not arrive as a brand-new booking.
    data.forEach((appointment) => known.add(appointment.id));

    fresh.forEach((appointment) => {
      show({
        id: appointment.id,
        title: "New booking",
        body: describe(appointment, zone),
        action: { label: "View booking", href: `/dashboard/${businessId}/appointments` },
      });
    });
  }, [data, businessId, zone, show]);
}

function describe(appointment: Appointment, zone: string | undefined) {
  const when = new Intl.DateTimeFormat("en-GB", {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone: zone,
  }).format(new Date(appointment.startsAt));
  const who = appointment.customerName ?? "A customer";
  const what = appointment.serviceName ?? "an appointment";
  return `${who} booked ${what} — ${when}.`;
}
