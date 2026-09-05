"use client";

/**
 * A failed request shown as a failure.
 *
 * <p>The alternative an agent reaches for by default is to leave the form looking idle, which reads
 * as "nothing happened" and invites the user to submit again.
 */
export function FormError({ message }: { message: string | null }) {
  if (!message) {
    return null;
  }
  return (
    <p role="alert" className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-800">
      {message}
    </p>
  );
}
