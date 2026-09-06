import Link from "next/link";

/**
 * The wordmark, and the only place the gradient appears as identity rather than as emphasis.
 *
 * <p>A mark rather than a logo file: Bookly has no brand asset, and inventing one in CSS keeps the
 * identity in the token system where it stays consistent with everything else.
 */
export function BrandMark({ href = "/" }: { href?: string }) {
  return (
    <Link href={href} className="inline-flex items-center gap-2 font-semibold tracking-tight">
      <span aria-hidden="true" className="bg-brand-gradient h-6 w-6 rounded-lg" />
      <span className="text-lg text-ink">Bookly</span>
    </Link>
  );
}
