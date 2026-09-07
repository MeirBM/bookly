"use client";

import { useState } from "react";

const SIZES = {
  sm: "h-10 w-10 rounded-lg text-base",
  md: "h-14 w-14 rounded-xl text-xl",
  lg: "h-16 w-16 rounded-xl text-2xl sm:h-20 sm:w-20 sm:text-3xl",
} as const;

/**
 * A business's logo, with the Bookly mark standing in.
 *
 * <p>The mark appears in two cases, not one: no logo set, and a logo that will not load. The second
 * is not hypothetical — the image lives on hosting nobody here controls, and a link that worked the
 * day it was pasted can rot, hotlink-block or go private at any time. Without `onError` that
 * becomes a broken-image glyph on the shop's own booking page, which reads as a broken shop.
 *
 * <p>The fallback is the gradient tile from {@link BrandMark} carrying a B, because at avatar size
 * there is no wordmark beside it to say whose mark it is.
 */
export function BusinessAvatar({
  src,
  name,
  size = "md",
}: {
  src: string | null | undefined;
  name: string;
  size?: keyof typeof SIZES;
}) {
  // Which address failed, rather than a bare "something failed": a different business, or a newly
  // typed URL, then gets its own attempt instead of inheriting the previous one's verdict.
  const [failedSrc, setFailedSrc] = useState<string | null>(null);
  const failed = failedSrc !== null && failedSrc === src;

  const shape = SIZES[size];

  if (!src || failed) {
    return (
      <span
        role="img"
        aria-label="Bookly"
        className={`bg-brand-gradient inline-flex shrink-0 items-center justify-center font-semibold text-white ${shape}`}
      >
        <span aria-hidden="true">B</span>
      </span>
    );
  }

  return (
    // Deliberately a plain <img>: next/image wants either a configured remote host or
    // unoptimized, and the whole point of this field is that the host is arbitrary and
    // owner-supplied. There is nothing to configure ahead of time.
    // eslint-disable-next-line @next/next/no-img-element
    <img
      src={src}
      alt={`${name} logo`}
      // The address is chosen by the business, so a visitor's browser fetches a host Bookly does
      // not control. no-referrer keeps that host from learning which booking page the visitor was
      // on. It cannot stop the request itself — an <img> is a request — so what remains is a
      // deliberate trade recorded in docs/audit/turn-4.md, not an oversight.
      //
      // Not crossOrigin="anonymous", which the review also suggested: that switches the fetch to
      // CORS mode, and an image host without Access-Control-Allow-Origin — which is most of them —
      // would then fail to load. It would harden nothing here and break the ordinary case.
      referrerPolicy="no-referrer"
      onError={() => setFailedSrc(src)}
      className={`shrink-0 border border-border bg-surface object-cover ${shape}`}
    />
  );
}
