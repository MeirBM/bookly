"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { useAuth } from "@/lib/auth-context";

/**
 * Criterion 1.17: an unauthenticated visitor to /dashboard is sent to /login.
 *
 * <p>This is a convenience, not a control. It hides a screen; it does not protect data. Every
 * tenant-scoped response is authorised on the server by TenantGuard, because the browser cannot be
 * trusted — its code runs in the open and anyone can skip this component entirely.
 *
 * <p>The redirect waits for `ready`. Without that wait it fires on the first paint, before
 * localStorage has been read, and bounces a signed-in user to the login page on every reload.
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
    return <p className="p-8 text-slate-600">Loading…</p>;
  }

  if (!tokens) {
    return <p className="p-8 text-slate-600">Redirecting to sign in…</p>;
  }

  return (
    <div className="flex min-h-full flex-1 flex-col">
      <header className="flex items-center justify-between border-b border-slate-200 bg-white px-6 py-3">
        <span className="font-semibold">Bookly</span>
        <button className="text-sm underline" type="button" onClick={signOut}>
          Sign out
        </button>
      </header>
      <main className="mx-auto w-full max-w-3xl flex-1 px-6 py-8">{children}</main>
    </div>
  );
}
