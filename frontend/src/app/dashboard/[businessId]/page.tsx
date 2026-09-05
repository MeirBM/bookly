"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { use } from "react";
import { AsyncSection } from "@/components/AsyncSection";
import { api } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";

/**
 * Overview. Its real job is criterion 2.21: a business with nothing configured is told what to do
 * next, in order, rather than shown three empty tables and left to infer the sequence.
 */
export default function BusinessOverviewPage({
  params,
}: {
  params: Promise<{ businessId: string }>;
}) {
  const { businessId } = use(params);
  const { tokens } = useAuth();
  const token = tokens?.accessToken ?? "";

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
    <section className="flex flex-col gap-4">
      <h1 className="text-2xl font-semibold">Overview</h1>

      <AsyncSection
        query={setup}
        label="this business"
        isEmpty={() => false}
        empty={null}
      >
        {({ services, employees }) => {
          const steps = [
            {
              done: services.length > 0,
              text: "Add the services you offer, each with how long it takes.",
              href: `/dashboard/${businessId}/services`,
              cta: "Add a service",
            },
            {
              done: employees.length > 0,
              text: "Add the people who perform them.",
              href: `/dashboard/${businessId}/employees`,
              cta: "Add an employee",
            },
            {
              done: employees.some((e) => e.serviceIds.length > 0),
              text: "Say who performs what, and when each person works.",
              href: `/dashboard/${businessId}/employees`,
              cta: "Set services and hours",
            },
          ];
          const next = steps.find((step) => !step.done);

          if (!next) {
            return (
              <div className="flex flex-col gap-3">
                <p className="text-slate-700">
                  {services.length} service{services.length === 1 ? "" : "s"} and{" "}
                  {employees.length} employee{employees.length === 1 ? "" : "s"} configured.
                </p>
                <Link
                  className="w-fit rounded-md bg-slate-900 px-4 py-2 font-medium text-white hover:bg-slate-700"
                  href={`/dashboard/${businessId}/availability`}
                >
                  Check availability
                </Link>
              </div>
            );
          }

          return (
            <div className="flex flex-col gap-4">
              <p className="text-slate-700">
                Bookly can only offer a time once it knows what you sell, who performs it and when
                they work. Three steps.
              </p>
              <ol className="flex flex-col gap-2">
                {steps.map((step) => (
                  <li key={step.text} className="flex items-start gap-2 text-slate-700">
                    <span aria-hidden="true">{step.done ? "✓" : "○"}</span>
                    <span className={step.done ? "text-slate-500 line-through" : ""}>
                      {step.text}
                    </span>
                  </li>
                ))}
              </ol>
              <Link
                className="w-fit rounded-md bg-slate-900 px-4 py-2 font-medium text-white hover:bg-slate-700"
                href={next.href}
              >
                {next.cta}
              </Link>
            </div>
          );
        }}
      </AsyncSection>
    </section>
  );
}
