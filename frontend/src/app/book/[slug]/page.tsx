"use client";

import { useMutation, useQuery } from "@tanstack/react-query";
import { use, useState } from "react";
import { Field, buttonClass, inputClass } from "@/components/Field";
import { FormError } from "@/components/FormError";
import { ApiError, api, type BookingConfirmation } from "@/lib/api";

/**
 * The public booking page. No account, by design.
 *
 * <p>The one path that needs care is a slot taken between page load and submit. It is not an edge
 * case — it is what happens whenever two people want the same time, which is the normal way a busy
 * shop fills up. Criterion 3.19: it must be reported and the slots refreshed, never swallowed and
 * never shown as a confirmation.
 */
export default function BookingPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = use(params);

  const [serviceId, setServiceId] = useState("");
  const [employeeId, setEmployeeId] = useState("");
  const [date, setDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [chosenSlot, setChosenSlot] = useState<string | null>(null);
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [phone, setPhone] = useState("");
  const [failure, setFailure] = useState<string | null>(null);
  const [confirmed, setConfirmed] = useState<BookingConfirmation | null>(null);

  const business = useQuery({
    queryKey: ["public-business", slug],
    queryFn: () => api.publicBusiness(slug),
    retry: false,
  });

  const service = business.data?.services.find((s) => s.id === serviceId)
    ?? business.data?.services[0];
  const activeServiceId = service?.id ?? "";

  // Only people who perform the chosen service. Offering the others would let a visitor
  // pick a combination that can never produce a slot.
  const eligible = (business.data?.employees ?? []).filter((employee) =>
    employee.serviceIds.includes(activeServiceId),
  );

  const availability = useQuery({
    queryKey: ["public-availability", slug, activeServiceId, employeeId, date],
    queryFn: () =>
      api.publicAvailability(slug, {
        serviceId: activeServiceId,
        employeeId: employeeId || undefined,
        date,
      }),
    enabled: Boolean(activeServiceId && date && business.data),
  });

  const book = useMutation({
    mutationFn: (start: string) =>
      api.publicBook(slug, {
        serviceId: activeServiceId,
        employeeId:
          employeeId
          || availability.data?.slots.find((slot) => slot.start === start)?.employeeIds[0]
          || "",
        startsAt: start,
        customerName: name.trim(),
        customerEmail: email.trim(),
        customerPhone: phone.trim() || undefined,
      }),
    onSuccess: (result) => {
      setFailure(null);
      setConfirmed(result);
    },
    onError: async (error) => {
      if (error instanceof ApiError && error.status === 409) {
        // Someone else took it while this page was open. Say so plainly and refresh, so the
        // visitor picks from what is actually free rather than retrying into the same wall.
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
    return <Shell><p role="status">Loading…</p></Shell>;
  }

  if (business.isError) {
    // A 404 and a server fault are different things to say to a visitor, and saying the wrong one
    // costs the business a customer. 3.17 requires *unknown* and *unbookable* to be
    // indistinguishable from each other; it says nothing about a 500, and telling someone "there
    // is no business at this address" during a transient outage means they do not come back — the
    // owner loses a booking they would have had and never learns why.
    const notFound = business.error instanceof ApiError && business.error.status === 404;
    return notFound ? (
      <Shell>
        <h1 className="text-2xl font-semibold">Nothing to book here</h1>
        <p className="mt-2 text-slate-600" role="alert">
          There is no business taking bookings at this address.
        </p>
      </Shell>
    ) : (
      <Shell>
        <h1 className="text-2xl font-semibold">Something went wrong</h1>
        <p className="mt-2 text-slate-600" role="alert">
          Could not load this booking page. This is our end, not yours — please try again.
        </p>
        <button
          className="mt-4 rounded-md border border-slate-300 px-4 py-2 text-sm hover:bg-slate-100"
          type="button"
          onClick={() => business.refetch()}
        >
          Try again
        </button>
      </Shell>
    );
  }

  if (confirmed) {
    const when = new Intl.DateTimeFormat("en-GB", {
      dateStyle: "full",
      timeStyle: "short",
      timeZone: confirmed.timezone,
    }).format(new Date(confirmed.startsAt));
    return (
      <Shell>
        <h1 className="text-2xl font-semibold">You are booked</h1>
        <p className="mt-3 text-slate-700">
          {confirmed.serviceName} with {confirmed.employeeName}
        </p>
        {/* The business's clock, not the visitor's: they are turning up at the shop. */}
        <p className="mt-1 text-slate-700" data-testid="confirmed-when">
          {when} ({confirmed.timezone})
        </p>
        <p className="mt-4 text-sm text-slate-600">Reference {confirmed.id.slice(0, 8)}</p>
      </Shell>
    );
  }

  const slots = availability.data?.slots ?? [];
  const zone = business.data.timezone;
  const formatter = new Intl.DateTimeFormat("en-GB", {
    hour: "2-digit",
    minute: "2-digit",
    timeZone: zone,
  });

  return (
    <Shell>
      <h1 className="text-2xl font-semibold">{business.data.name}</h1>
      <p className="mt-1 text-sm text-slate-600">Times shown in {zone}.</p>

      <form className="mt-6 flex flex-wrap items-end gap-3">
        <label className="text-sm">
          <span className="block text-slate-700">Service</span>
          <select
            className={inputClass}
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
          </select>
        </label>

        <label className="text-sm">
          <span className="block text-slate-700">With</span>
          <select
            className={inputClass}
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
          </select>
        </label>

        <label className="text-sm">
          <span className="block text-slate-700">Date</span>
          <input
            className={inputClass}
            type="date"
            value={date}
            onChange={(event) => {
              setDate(event.target.value);
              setChosenSlot(null);
            }}
          />
        </label>
      </form>

      <section className="mt-6">
        <FormError message={failure} />

        {availability.isPending ? (
          <p className="py-4 text-slate-600" role="status">
            Finding free times…
          </p>
        ) : availability.isError ? (
          <div role="alert">
            <p className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-800">
              Could not load times.
            </p>
            <button
              className="mt-2 text-sm underline"
              type="button"
              onClick={() => availability.refetch()}
            >
              Try again
            </button>
          </div>
        ) : slots.length === 0 ? (
          <p className="rounded-md border border-dashed border-slate-300 p-6 text-slate-600">
            No free times on this date. Try another day.
          </p>
        ) : (
          <ul className="flex flex-wrap gap-2" data-testid="slots">
            {slots.map((slot) => (
              <li key={slot.start}>
                <button
                  type="button"
                  aria-pressed={chosenSlot === slot.start}
                  className={`rounded-md border px-3 py-2 text-sm ${
                    chosenSlot === slot.start
                      ? "border-slate-900 bg-slate-900 text-white"
                      : "border-slate-300 bg-white"
                  }`}
                  onClick={() => setChosenSlot(slot.start)}
                >
                  {formatter.format(new Date(slot.start))}
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>

      {chosenSlot ? (
        <form
          className="mt-6 flex max-w-sm flex-col gap-4"
          onSubmit={(event) => {
            event.preventDefault();
            book.mutate(chosenSlot);
          }}
        >
          <h2 className="text-lg font-semibold">
            Your details for {formatter.format(new Date(chosenSlot))}
          </h2>
          <Field label="Name">
            <input
              className={inputClass}
              required
              value={name}
              onChange={(event) => setName(event.target.value)}
            />
          </Field>
          <Field label="Email">
            <input
              className={inputClass}
              type="email"
              required
              value={email}
              onChange={(event) => setEmail(event.target.value)}
            />
          </Field>
          <Field label="Phone (optional)">
            <input
              className={inputClass}
              value={phone}
              onChange={(event) => setPhone(event.target.value)}
            />
          </Field>
          <button className={buttonClass} type="submit" disabled={book.isPending}>
            {book.isPending ? "Booking…" : "Confirm booking"}
          </button>
        </form>
      ) : null}
    </Shell>
  );
}

function Shell({ children }: { children: React.ReactNode }) {
  return <main className="mx-auto w-full max-w-2xl flex-1 px-6 py-12">{children}</main>;
}
