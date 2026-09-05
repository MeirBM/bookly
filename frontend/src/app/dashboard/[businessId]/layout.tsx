"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { use } from "react";

const TABS = [
  { href: "", label: "Overview" },
  { href: "/services", label: "Services" },
  { href: "/employees", label: "Employees" },
  { href: "/availability", label: "Availability" },
];

export default function BusinessLayout({
  children,
  params,
}: {
  children: React.ReactNode;
  params: Promise<{ businessId: string }>;
}) {
  const { businessId } = use(params);
  const pathname = usePathname();
  const base = `/dashboard/${businessId}`;

  return (
    <div className="flex flex-col gap-6">
      <nav className="flex gap-1 border-b border-slate-200" aria-label="Business sections">
        {TABS.map((tab) => {
          const href = `${base}${tab.href}`;
          const active = pathname === href;
          return (
            <Link
              key={tab.label}
              href={href}
              aria-current={active ? "page" : undefined}
              className={`px-3 py-2 text-sm ${
                active
                  ? "border-b-2 border-slate-900 font-medium text-slate-900"
                  : "text-slate-600 hover:text-slate-900"
              }`}
            >
              {tab.label}
            </Link>
          );
        })}
      </nav>
      {children}
    </div>
  );
}
