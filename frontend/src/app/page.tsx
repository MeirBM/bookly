import Link from "next/link";
import { BrandMark } from "@/components/layout/BrandMark";

export default function Home() {
  return (
    <main className="mx-auto flex w-full max-w-3xl flex-1 flex-col justify-center gap-8 px-4 py-16 sm:px-6">
      <BrandMark />

      <div>
        <h1 className="text-4xl font-semibold tracking-tight text-ink sm:text-5xl">
          Let customers book the time you are{" "}
          <span className="text-brand-gradient">actually free</span>.
        </h1>
        <p className="mt-4 max-w-xl text-base text-ink-muted">
          Bookly works out real availability from your opening hours, each person&rsquo;s working
          hours, how long a service takes and what is already booked — so nobody has to answer a
          message to say when you are free.
        </p>
      </div>

      <div className="flex flex-wrap gap-3">
        <Link
          href="/register"
          className="bg-brand-gradient inline-flex h-11 items-center justify-center rounded-md px-5 text-sm font-medium text-white shadow-card transition-[box-shadow,filter] duration-150 hover:shadow-card-hover hover:brightness-105"
        >
          Create an account
        </Link>
        <Link
          href="/login"
          className="inline-flex h-11 items-center justify-center rounded-md border border-border-strong bg-surface px-5 text-sm font-medium text-ink transition-colors duration-150 hover:bg-surface-muted"
        >
          Sign in
        </Link>
      </div>

      <dl className="grid gap-6 border-t border-border pt-8 sm:grid-cols-3">
        {[
          ["Two people, one slot", "The database refuses the second booking, so a double booking is impossible rather than unlikely."],
          ["Across time zones", "Every time is shown on the business's clock, including the days the clocks change."],
          ["No account to book", "A customer picks a time and leaves their name. That is the whole flow."],
        ].map(([term, detail]) => (
          <div key={term}>
            <dt className="text-sm font-semibold text-ink">{term}</dt>
            <dd className="mt-1 text-sm text-ink-muted">{detail}</dd>
          </div>
        ))}
      </dl>
    </main>
  );
}
