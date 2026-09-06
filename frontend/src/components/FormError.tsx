"use client";

/**
 * A failed request, shown as a failure.
 *
 * <p>The alternative an agent reaches for by default is to leave the form looking idle, which reads
 * as "nothing happened" and invites the reader to submit again. Announced with `role="alert"` so
 * the failure is heard as well as seen.
 */
export function FormError({ message }: { message: string | null }) {
  if (!message) {
    return null;
  }
  return (
    <p
      role="alert"
      className="rounded-md border border-danger-200 bg-danger-50 px-3.5 py-2.5 text-sm text-danger-700"
    >
      {message}
    </p>
  );
}
