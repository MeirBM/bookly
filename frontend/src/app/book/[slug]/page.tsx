"use client";

import { useMutation, useQuery } from "@tanstack/react-query";
import { use, useState } from "react";
import { BookingConfirmation } from "@/components/booking/BookingConfirmation";
import { BookingSteps } from "@/components/booking/BookingSteps";
import { CustomerDetailsForm } from "@/components/booking/CustomerDetailsForm";
import { SlotGrid } from "@/components/booking/SlotGrid";
import { BusinessAvatar } from "@/components/ui/BusinessAvatar";
import { FormError } from "@/components/FormError";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { Input, Select } from "@/components/ui/Input";
import { SkeletonRows } from "@/components/ui/Skeleton";
import { ApiError, api, type BookingConfirmation as Confirmation } from "@/lib/api";
import { todayIn } from "@/lib/calendar-dates";

/**
 * The public booking page: no account, no token.
 *
 * <p>The path a visitor takes is service, person, date, time, details, confirmation — and the page
 * shows where they are, because a form of unknown length feels long even when it is short.
 *
 * <p>The one case that needs care is a slot taken between page load and submit. It is not an edge
 * case: it is what happens whenever two people want the same time, which is how a busy shop fills
 * up. It must be reported and the times refreshed, never swallowed and never shown as a success.
 */
export default function BookingPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = use(params);

  const [serviceId, setServiceId] = useState("");
  const [employeeId, setEmployeeId] = useState("");
  // Empty until the business's zone is known, then defaulted to today *there*. Seeding it from
  // toISOString() offered a visitor east of UTC tomorrow's slots under the heading "today" every
  // evening, which is the same defect the calendar had.
  const [date, setDate] = useState("");
  const [chosenSlot, setChosenSlot] = useState<string | null>(null);
  const [failure, setFailure] = useState<string | null>(null);
  const [confirmed, setConfirmed] = useState<Confirmation | null>(null);

  const business = useQuery({
    queryKey: ["public-business", slug],
    queryFn: () => api.publicBusiness(slug),
    retry: false,
  });

  const service =
    business.data?.services.find((s) => s.id === serviceId) ?? business.data?.services[0];
  const activeServiceId = service?.id ?? "";

  // Same idiom as activeServiceId: the state holds only what the visitor has chosen, and the
  // default comes from the business once it is known.
  const activeDate = date || (business.data ? todayIn(business.data.timezone) : "");

  // Only people who perform the chosen service. Offering the rest would let a visitor pick a
  // combination that can never produce a time.
  const eligible = (business.data?.employees ?? []).filter((employee) =>
    employee.serviceIds.includes(activeServiceId),
  );

  const availability = useQuery({
    queryKey: ["public-availability", slug, activeServiceId, employeeId, activeDate],
    queryFn: () =>
      api.publicAvailability(slug, {
        serviceId: activeServiceId,
        employeeId: employeeId || undefined,
        date: activeDate,
      }),
    enabled: Boolean(activeServiceId && activeDate && business.data),
  });

  const book = useMutation({
    mutationFn: (details: { name: string; email: string; phone: string }) =>
      api.publicBook(slug, {
        serviceId: activeServiceId,
        employeeId:
          employeeId
          || availability.data?.slots.find((slot) => slot.start === chosenSlot)?.employeeIds[0]
          || "",
        startsAt: chosenSlot ?? "",
        customerName: details.name,
        customerEmail: details.email,
        customerPhone: details.phone || undefined,
      }),
    onSuccess: (result) => {
      setFailure(null);
      setConfirmed(result);
    },
    onError: async (error) => {
      if (error instanceof ApiError && error.status === 409) {
        // Someone else took it while this page was open. Say so plainly and refresh, so the
        // visitor chooses from what is free rather than retrying into the same wall.
        setFailure(
          error.body.code === "SLOT_TAKEN"
            ? "Someone just booked that time. Here are the times still free."
            : error.body.message,
        );
        setChosenSlot(null);
        await availability.refetch();
        return;
      }
      setFailure(
        error instanceof ApiError ? error.body.message : "Could not reach the server. Try again.",
      );
    },
  });

  if (business.isPending) {
    return (
      <Shell>
        <div role="status" aria-live="polite" className="flex flex-col gap-4">
          <p className="text-sm text-ink-subtle">Loading this business…</p>
          <SkeletonRows rows={2} />
        </div>
      </Shell>
    );
  }

  if (business.isError) {
    // Criterion 3.17 makes an unknown address and a business that is not open for booking the same
    // answer, so this page cannot tell them apart either — but a *fault* is a different thing to
    // say, and saying the wrong one loses the business a customer who would have come back.
    const notFound = business.error instanceof ApiError && business.error.status === 404;
    return (
      <Shell>
        <Card className="text-center">
          <h1 className="text-xl font-semibold text-ink">
            {notFound ? "Nothing to book here" : "Something went wrong"}
          </h1>
          <p className="mx-auto mt-2 max-w-sm text-sm text-ink-muted" role="alert">
            {notFound
              ? "There is no business taking bookings at this address."
              : "Could not load this booking page. This is our end, not yours — please try again."}
          </p>
          {notFound ? null : (
            <div className="mt-5 flex justify-center">
              <Button variant="secondary" type="button" onClick={() => business.refetch()}>
                Try again
              </Button>
            </div>
          )}
        </Card>
      </Shell>
    );
  }

  if (confirmed) {
    return (
      <Shell>
        <BookingConfirmation confirmation={confirmed} />
      </Shell>
    );
  }

  const zone = business.data.timezone;
  const formatter = new Intl.DateTimeFormat("en-GB", {
    hour: "2-digit",
    minute: "2-digit",
    timeZone: zone,
  });
  const format = (instant: string) => formatter.format(new Date(instant));
  const slots = availability.data?.slots ?? [];

  return (
    <Shell>
      <header className="flex flex-col gap-3">
        <div className="flex items-center gap-4">
          <BusinessAvatar src={business.data.logoUrl} name={business.data.name} size="lg" />
          <div className="min-w-0">
            <h1 className="text-3xl font-semibold tracking-tight text-ink sm:text-4xl">
              {business.data.name}
            </h1>
            <p className="mt-1.5 text-sm text-ink-muted">
              Book in a few taps — no account needed. Times shown in {zone}.
            </p>
          </div>
        </div>
        <BookingSteps current={chosenSlot ? "Details" : "Time"} />
      </header>

      <Card className="flex flex-col gap-4">
        <div className="grid gap-4 sm:grid-cols-3">
          <label className="text-sm">
            <span className="mb-1.5 block font-medium text-ink">Service</span>
            <Select
              value={activeServiceId}
              onChange={(event) => {
                setServiceId(event.target.value);
                setEmployeeId("");
                setChosenSlot(null);
              }}
            >
              {business.data.services.map((option) => (
                <option key={option.id} value={option.id}>
                  {option.name} ({option.durationMinutes} min)
                </option>
              ))}
            </Select>
          </label>

          <label className="text-sm">
            <span className="mb-1.5 block font-medium text-ink">With</span>
            <Select
              value={employeeId}
              onChange={(event) => {
                setEmployeeId(event.target.value);
                setChosenSlot(null);
              }}
            >
              <option value="">Anyone available</option>
              {eligible.map((employee) => (
                <option key={employee.id} value={employee.id}>
                  {employee.name}
                </option>
              ))}
            </Select>
          </label>

          <label className="text-sm">
            <span className="mb-1.5 block font-medium text-ink">Date</span>
            <Input
              type="date"
              value={activeDate}
              onChange={(event) => {
                setDate(event.target.value);
                setChosenSlot(null);
              }}
            />
          </label>
        </div>
      </Card>

      <section className="flex flex-col gap-4">
        <FormError message={failure} />

        {availability.isPending ? (
          <div role="status" aria-live="polite" className="flex flex-col gap-3">
            <p className="text-sm text-ink-subtle">Loading free times…</p>
            <div className="grid grid-cols-3 gap-2 sm:grid-cols-4 md:grid-cols-6">
              {Array.from({ length: 12 }, (_, i) => (
                <div key={i} className="h-11 animate-pulse rounded-md bg-surface-muted" />
              ))}
            </div>
          </div>
        ) : availability.isError ? (
          <div role="alert" className="flex flex-col items-start gap-3">
            <p className="rounded-md border border-danger-200 bg-danger-50 px-3.5 py-2.5 text-sm text-danger-700">
              Could not load times.
            </p>
            <Button variant="secondary" size="sm" type="button" onClick={() => availability.refetch()}>
              Try again
            </Button>
          </div>
        ) : slots.length === 0 ? (
          <div className="rounded-lg border border-dashed border-border-strong bg-surface px-6 py-10 text-center">
            <p className="font-medium text-ink">No free times on this date</p>
            <p className="mx-auto mt-1.5 max-w-md text-sm text-ink-muted">
              Nobody who performs this service works today, the day is full, or the gaps left are
              too short for it. Try another day.
            </p>
          </div>
        ) : (
          <>
            <p className="text-sm text-ink-muted">
              {slots.length} time{slots.length === 1 ? "" : "s"} free
              {service ? ` · ${service.durationMinutes} minutes each` : ""}
            </p>
            <SlotGrid
              slots={slots}
              selected={chosenSlot}
              format={format}
              onSelect={setChosenSlot}
            />
          </>
        )}
      </section>

      {chosenSlot ? (
        <Card>
          <CustomerDetailsForm
            when={`${format(chosenSlot)} on ${new Intl.DateTimeFormat("en-GB", {
              dateStyle: "full",
              timeZone: zone,
            }).format(new Date(chosenSlot))}`}
            pending={book.isPending}
            failure={null}
            onSubmit={(details) => book.mutate(details)}
          />
        </Card>
      ) : null}
    </Shell>
  );
}

function Shell({ children }: { children: React.ReactNode }) {
  return (
    <main className="mx-auto flex w-full max-w-3xl flex-1 flex-col gap-6 px-4 py-8 sm:px-6 sm:py-12">
      {children}
    </main>
  );
}
