"use client";

import { use } from "react";
import { BusinessNav } from "@/components/layout/BusinessNav";

export default function BusinessLayout({
  children,
  params,
}: {
  children: React.ReactNode;
  params: Promise<{ businessId: string }>;
}) {
  const { businessId } = use(params);

  return (
    <div className="flex flex-col gap-6 lg:flex-row lg:gap-10">
      <BusinessNav businessId={businessId} />
      <div className="min-w-0 flex-1">{children}</div>
    </div>
  );
}
