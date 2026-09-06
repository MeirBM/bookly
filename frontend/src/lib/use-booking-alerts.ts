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

function isoDate(offsetDays: number) {
  const date = new Date();
  date.setDate(date.getDate() + offsetDays);
  return date.toISOString().slice(0, 10);
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

  const from = isoDate(0);
  const to = isoDate(WINDOW_DAYS);

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

  const zone = business.data?.timezone;

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
