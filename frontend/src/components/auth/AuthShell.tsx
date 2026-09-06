import Link from "next/link";
import type { ReactNode } from "react";
import { BrandMark } from "@/components/layout/BrandMark";
import { Card } from "@/components/ui/Card";

/**
 * The frame around signing in and signing up.
 *
 * <p>One frame for both, so the two screens cannot drift apart — they are the same moment from two
 * directions, and a visitor who mistakes one for the other should not notice a different product.
 */
export function AuthShell({
  title,
  subtitle,
  children,
  footer,
}: {
  title: string;
  subtitle: string;
  children: ReactNode;
  footer: { prompt: string; href: string; label: string };
}) {
  return (
    <main className="mx-auto flex w-full max-w-md flex-1 flex-col justify-center gap-6 px-4 py-12">
      <BrandMark />
      <div>
        <h1 className="text-2xl font-semibold tracking-tight text-ink">{title}</h1>
        <p className="mt-1.5 text-sm text-ink-muted">{subtitle}</p>
      </div>
      <Card>{children}</Card>
      <p className="text-center text-sm text-ink-muted">
        {footer.prompt}{" "}
        <Link className="font-medium text-brand-700 hover:text-brand-800" href={footer.href}>
          {footer.label}
        </Link>
      </p>
    </main>
  );
}
