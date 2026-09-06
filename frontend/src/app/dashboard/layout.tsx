"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { BrandMark } from "@/components/layout/BrandMark";
import { Button } from "@/components/ui/Button";
import { SkeletonRows } from "@/components/ui/Skeleton";
import { useAuth } from "@/lib/auth-context";

/**
 * The signed-in shell.
 *
 * <p>The redirect is a convenience, not a control. It hides a screen; it does not protect data.
 * Every tenant-scoped response is authorised on the server, because the browser runs in the open
 * and anyone can skip this component entirely.
 *
 * <p>It waits for `ready`. Without that wait it fires on the first paint, before localStorage has
 * been read, and bounces a signed-in visitor to the login page on every reload.
 */
export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const { tokens, ready, signOut } = useAuth();

  useEffect(() => {
    if (ready && !tokens) {
      router.replace("/login");
    }
  }, [ready, tokens, router]);

  if (!ready) {
    return (
      <div role="status" aria-live="polite" className="mx-auto w-full max-w-3xl px-4 py-10">
        <p className="text-sm text-ink-subtle">Loading…</p>
        <div className="mt-4">
          <SkeletonRows rows={2} />
        </div>
      </div>
    );
  }

  if (!tokens) {
    return (
      <p role="status" className="mx-auto w-full max-w-3xl px-4 py-10 text-sm text-ink-subtle">
        Redirecting to sign in…
      </p>
    );
  }

  return (
    <div className="flex min-h-full flex-1 flex-col">
      <header className="sticky top-0 z-10 border-b border-border bg-surface/85 backdrop-blur">
        <div className="mx-auto flex w-full max-w-6xl items-center justify-between gap-4 px-4 py-3 sm:px-6">
          <BrandMark href="/dashboard" />
          <Button variant="ghost" size="sm" type="button" onClick={signOut}>
            Sign out
          </Button>
        </div>
      </header>
      <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-6 sm:px-6 sm:py-8">{children}</main>
    </div>
  );
}
