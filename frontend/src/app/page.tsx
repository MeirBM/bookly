import Link from "next/link";

export default function Home() {
  return (
    <main className="mx-auto flex max-w-xl flex-1 flex-col justify-center gap-6 px-6 py-16">
      <div>
        <h1 className="text-3xl font-semibold">Bookly</h1>
        <p className="mt-2 text-slate-600">
          Let customers book the time you are actually free, without you answering a
          message to tell them.
        </p>
      </div>
      <div className="flex gap-3">
        <Link
          href="/register"
          className="rounded-md bg-slate-900 px-4 py-2 font-medium text-white hover:bg-slate-700"
        >
          Create an account
        </Link>
        <Link
          href="/login"
          className="rounded-md border border-slate-300 px-4 py-2 font-medium hover:bg-slate-100"
        >
          Sign in
        </Link>
      </div>
    </main>
  );
}
