"use client";

import type { Employee, ServiceOffering } from "@/lib/api";

/**
 * Which services one person performs.
 *
 * <p>Toggles rather than a multi-select, because the set is small and the state has to be readable
 * at a glance: an employee linked to nothing is offered for nothing, and that should be visible
 * without opening a control. `aria-pressed` carries the state for anyone not reading the colour.
 */
export function ServicePicker({
  employee,
  services,
  disabled = false,
  onToggle,
}: {
  employee: Employee;
  services: ServiceOffering[] | undefined;
  disabled?: boolean;
  onToggle: (serviceIds: string[]) => void;
}) {
  if (!services || services.length === 0) {
    return (
      <p className="mt-1.5 text-sm text-ink-muted">
        Add a service first — an employee linked to nothing is offered for nothing.
      </p>
    );
  }

  return (
    <div className="mt-2.5 flex flex-wrap gap-2">
      {services.map((service) => {
        const on = employee.serviceIds.includes(service.id);
        return (
          <button
            key={service.id}
            type="button"
            aria-pressed={on}
            disabled={disabled}
            onClick={() =>
              onToggle(
                on
                  ? employee.serviceIds.filter((id) => id !== service.id)
                  : [...employee.serviceIds, service.id],
              )
            }
            className={[
              "rounded-full border px-3 py-1.5 text-sm font-medium",
              "transition-colors duration-150 disabled:cursor-not-allowed disabled:opacity-55",
              on
                ? "border-transparent bg-brand-600 text-white hover:bg-brand-700"
                : "border-border-strong bg-surface text-ink-muted hover:border-ink-faint hover:text-ink",
            ].join(" ")}
          >
            {service.name}
          </button>
        );
      })}
    </div>
  );
}
