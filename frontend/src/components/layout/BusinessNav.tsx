"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

const TABS = [
  { href: "", label: "Overview" },
  { href: "/services", label: "Services" },
  { href: "/employees", label: "Employees" },
  { href: "/availability", label: "Availability" },
  { href: "/appointments", label: "Appointments" },
  { href: "/calendar", label: "Calendar" },
] as const;

/**
 * Navigation within one business.
 *
 * <p>A column on a wide screen and a scrolling row on a narrow one, rather than the same row
 * shrunk until the labels collide. Each target is at least 44px tall in both arrangements, and the
 * current page is marked with `aria-current` as well as with weight and fill.
 */
export function BusinessNav({ businessId }: { businessId: string }) {
  const pathname = usePathname();
  const base = `/dashboard/${businessId}`;

  return (
    <nav aria-label="Business sections" className="lg:w-52 lg:shrink-0">
      <ul
        className={[
          "flex gap-1 overflow-x-auto pb-1",
          "[scrollbar-width:none] [&::-webkit-scrollbar]:hidden",
          "lg:flex-col lg:overflow-visible lg:pb-0",
        ].join(" ")}
      >
        {TABS.map((tab) => {
          const href = `${base}${tab.href}`;
          const active = pathname === href;
          return (
            <li key={tab.label} className="shrink-0 lg:shrink">
              <Link
                href={href}
                aria-current={active ? "page" : undefined}
                className={[
                  "flex h-11 items-center rounded-md px-3 text-sm font-medium",
                  "transition-colors duration-150 lg:w-full",
                  active
                    ? "bg-brand-50 text-brand-700"
                    : "text-ink-muted hover:bg-surface-muted hover:text-ink",
                ].join(" ")}
              >
                {tab.label}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
