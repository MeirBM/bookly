"use client";

import { use } from "react";
import { BusinessNav } from "@/components/layout/BusinessNav";
import { useBookingAlerts } from "@/lib/use-booking-alerts";

export default function BusinessLayout({
  children,
  params,
}: {
  children: React.ReactNode;
  params: Promise<{ businessId: string }>;
}) {
  const { businessId } = use(params);

  // Mounted on the layout rather than on one page, so alerts keep arriving as the owner moves
  // between sections — and stop the moment they leave the dashboard entirely.
  useBookingAlerts(businessId);

  return (
    <div className="flex flex-col gap-6 lg:flex-row lg:gap-10">
      <div className="flex flex-col gap-3 lg:w-52 lg:shrink-0">
        <BusinessNav businessId={businessId} />
        {/* Said plainly and once. The alerts are a polled browser tab, and nothing here should
            leave an owner believing Bookly can reach them after they close it. */}
        <p className="px-3 text-xs leading-relaxed text-ink-faint">
          Booking notifications are active while your Bookly dashboard is open.
        </p>
      </div>
      <div className="min-w-0 flex-1">{children}</div>
    </div>
  );
}
