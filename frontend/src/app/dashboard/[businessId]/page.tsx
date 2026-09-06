"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { use } from "react";
import { AsyncSection } from "@/components/AsyncSection";
import { LogoField } from "@/components/business/LogoField";
import { BusinessAvatar } from "@/components/ui/BusinessAvatar";
import { Card } from "@/components/ui/Card";
import { PageHeader } from "@/components/ui/PageHeader";
import { api } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { useBusiness } from "@/lib/use-business";

/**
 * Overview.
 *
 * <p>Its real job is to answer "what do I do next?". Bookly can only offer a time once it knows
 * what is sold, who performs it and when they work, and a new business has none of those — so the
 * screen shows the three steps in order rather than three empty tables and an inference.
 */
export default function BusinessOverviewPage({
  params,
}: {
  params: Promise<{ businessId: string }>;
}) {
  const { businessId } = use(params);
  const { tokens } = useAuth();
  const token = tokens?.accessToken ?? "";
  const business = useBusiness(businessId);

  const setup = useQuery({
    queryKey: ["setup", businessId],
    queryFn: async () => {
      const [services, employees] = await Promise.all([
        api.listServices(token, businessId),
        api.listEmployees(token, businessId),
      ]);
      return { services, employees };
    },
    enabled: Boolean(token),
  });

  return (
    <div className="flex flex-col gap-8">
      <PageHeader
        leading={
          business.data ? (
            <BusinessAvatar src={business.data.logoUrl} name={business.data.name} />
          ) : null
        }
        title={business.data?.name ?? "Overview"}
        description={
          business.data ? (
            <>
              Public booking page:{" "}
              <span className="font-medium text-ink">/book/{business.data.slug}</span> · times shown
              to customers in {business.data.timezone}
            </>
          ) : undefined
        }
      />

      <AsyncSection query={setup} label="this business" isEmpty={() => false} empty={null}>
        {({ services, employees }) => {
          const steps = [
            {
              done: services.length > 0,
              title: "Add your services",
              detail: "What a customer books, and how long it takes.",
              href: `/dashboard/${businessId}/services`,
              cta: "Add a service",
            },
            {
              done: employees.length > 0,
              title: "Add the people who perform them",
              detail: "Availability is worked out per person.",
              href: `/dashboard/${businessId}/employees`,
              cta: "Add an employee",
            },
            {
              done: employees.some((e) => e.serviceIds.length > 0),
              title: "Say who performs what, and when they work",
              detail: "Someone with no services or no hours is never offered.",
              href: `/dashboard/${businessId}/employees`,
              cta: "Set services and hours",
            },
          ];
          const next = steps.find((step) => !step.done);

          if (!next) {
            return (
              <div className="flex flex-col gap-5">
                {/* Said in words as well as counted in tiles: a number beside a label tells a
                    reader how many, and a sentence tells them what it means. */}
                <p className="text-sm text-ink-muted">
                  Ready to take bookings — {services.length} service
                  {services.length === 1 ? "" : "s"} and {employees.length}{" "}
                  {employees.length === 1 ? "person" : "people"} set up.
                </p>
                <div className="grid gap-4 sm:grid-cols-3">
                  <Stat label="Services" value={services.length} />
                  <Stat label="People" value={employees.length} />
                  <Card className="flex flex-col justify-between gap-3">
                    <p className="text-sm text-ink-muted">Nothing left to configure.</p>
                    <Link
                      href={`/dashboard/${businessId}/calendar`}
                      className="text-sm font-medium text-brand-700 hover:text-brand-800"
                    >
                      Check the calendar →
                    </Link>
                  </Card>
                </div>
              </div>
            );
          }

          return (
            <Card className="flex flex-col gap-5">
              <p className="text-sm text-ink-muted">
                Bookly can only offer a time once it knows what you sell, who performs it and when
                they work. Three steps.
              </p>
              <ol className="flex flex-col gap-4">
                {steps.map((step) => (
                  <li key={step.title} className="flex gap-3">
                    <span
                      aria-hidden="true"
                      className={[
                        "mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full text-[11px] font-semibold",
                        step.done
                          ? "bg-brand-600 text-white"
                          : "border border-border-strong text-ink-faint",
                      ].join(" ")}
                    >
                      {step.done ? "✓" : ""}
                    </span>
                    <div className="min-w-0">
                      <p
                        className={
                          step.done ? "text-sm text-ink-subtle line-through" : "text-sm font-medium text-ink"
                        }
                      >
                        {step.title}
                        <span className="sr-only">{step.done ? " — done" : " — still to do"}</span>
                      </p>
                      <p className="mt-0.5 text-sm text-ink-subtle">{step.detail}</p>
                    </div>
                  </li>
                ))}
              </ol>
              <Link
                href={next.href}
                className="bg-brand-gradient inline-flex h-11 w-fit items-center justify-center rounded-md px-5 text-sm font-medium text-white shadow-card transition-[box-shadow,filter] duration-150 hover:shadow-card-hover hover:brightness-105"
              >
                {next.cta}
              </Link>
            </Card>
          );
        }}
      </AsyncSection>

      {business.data ? <LogoField business={business.data} /> : null}
    </div>
  );
}

function Stat({ label, value }: { label: string; value: number }) {
  return (
    <Card>
      <p className="text-xs font-medium uppercase tracking-wide text-ink-subtle">{label}</p>
      <p className="mt-1 text-3xl font-semibold tabular-nums text-ink">{value}</p>
    </Card>
  );
}
