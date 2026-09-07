"use client";

import Link from "next/link";
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";

export type Toast = {
  id: string;
  title: string;
  body: string;
  action?: { label: string; href: string };
};

type ToastApi = { show: (toast: Toast) => void };

const ToastContext = createContext<ToastApi | null>(null);

const VISIBLE_FOR_MS = 9_000;
/** Three at once is a notice; a column of them is a wall someone has to clear. */
const MAX_VISIBLE = 3;

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const timers = useRef(new Map<string, ReturnType<typeof setTimeout>>());

  const dismiss = useCallback((id: string) => {
    setToasts((current) => current.filter((toast) => toast.id !== id));
    const timer = timers.current.get(id);
    if (timer) {
      clearTimeout(timer);
      timers.current.delete(id);
    }
  }, []);

  const show = useCallback(
    (toast: Toast) => {
      setToasts((current) => {
        // The caller is responsible for not repeating itself, but a duplicate id here would
        // stack identical cards, so it is refused at the door as well.
        if (current.some((existing) => existing.id === toast.id)) {
          return current;
        }
        return [...current, toast].slice(-MAX_VISIBLE);
      });
      timers.current.set(
        toast.id,
        setTimeout(() => dismiss(toast.id), VISIBLE_FOR_MS),
      );
    },
    [dismiss],
  );

  // Timers outlive the component if nobody clears them, and a timer firing into an unmounted
  // tree is a leak that only shows up as a warning nobody reads.
  useEffect(() => {
    const pending = timers.current;
    return () => {
      pending.forEach((timer) => clearTimeout(timer));
      pending.clear();
    };
  }, []);

  const api = useMemo(() => ({ show }), [show]);

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div
        // polite, not assertive: a booking is worth knowing about, not worth interrupting
        // whatever someone is in the middle of reading.
        role="status"
        aria-live="polite"
        className="pointer-events-none fixed inset-x-0 bottom-0 z-50 flex flex-col items-center gap-2 p-4 sm:inset-x-auto sm:right-0 sm:items-end sm:p-6"
      >
        {toasts.map((toast) => (
          <ToastCard key={toast.id} toast={toast} onDismiss={() => dismiss(toast.id)} />
        ))}
      </div>
    </ToastContext.Provider>
  );
}

function ToastCard({ toast, onDismiss }: { toast: Toast; onDismiss: () => void }) {
  return (
    <div className="pointer-events-auto w-full max-w-sm animate-[toast-in_180ms_var(--ease-out-soft)] rounded-lg border border-border bg-surface p-4 shadow-pop">
      <div className="flex gap-3">
        <span
          aria-hidden="true"
          className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-brand-50 text-brand-600"
        >
          <BellIcon />
        </span>
        <div className="min-w-0 flex-1">
          <p className="text-sm font-semibold text-ink">{toast.title}</p>
          <p className="mt-0.5 text-sm text-ink-muted">{toast.body}</p>
          {toast.action ? (
            <Link
              href={toast.action.href}
              onClick={onDismiss}
              className="mt-2 inline-block text-sm font-medium text-brand-700 hover:text-brand-800"
            >
              {toast.action.label}
            </Link>
          ) : null}
        </div>
        <button
          type="button"
          onClick={onDismiss}
          aria-label="Dismiss notification"
          className="-mt-1 -mr-1 h-8 w-8 shrink-0 rounded-md text-ink-faint transition-colors duration-150 hover:bg-surface-muted hover:text-ink"
        >
          <span aria-hidden="true">×</span>
        </button>
      </div>
    </div>
  );
}

/** An icon, not an emoji: the design standard rules emoji out as UI iconography. */
function BellIcon() {
  return (
    <svg viewBox="0 0 20 20" className="h-4 w-4" fill="none" aria-hidden="true">
      <path
        d="M10 3a4 4 0 0 0-4 4v3l-1.2 2.1a.5.5 0 0 0 .43.75h9.54a.5.5 0 0 0 .43-.75L14 10V7a4 4 0 0 0-4-4Z"
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinejoin="round"
      />
      <path d="M8.2 15.2a2 2 0 0 0 3.6 0" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
    </svg>
  );
}

export function useToast(): ToastApi {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error("useToast must be used inside ToastProvider");
  }
  return context;
}
