"use client";

import { createContext, useCallback, useContext, useMemo, useSyncExternalStore } from "react";
import { api, type TokenPair } from "./api";

/**
 * Session state for the browser.
 *
 * <p>Tokens are held in localStorage so a reload does not log the user out. That places them within
 * reach of any script running on this origin, which is a real trade-off: an httpOnly cookie would
 * resist XSS better, but it would also make the API browser-shaped, and the same endpoints have to
 * serve the Android and iOS clients that come later. The mitigations that matter are enforced on
 * the server — a short access-token lifetime, and refresh rotation with reuse detection. Recorded
 * in the turn-1 audit as an accepted limitation rather than an oversight.
 *
 * <p>Read through {@link useSyncExternalStore} rather than an effect. localStorage is genuinely
 * external state that server rendering cannot see, and this is the API React provides for exactly
 * that: it gives a correct server snapshot with no hydration mismatch, and no setState during an
 * effect.
 */
const STORAGE_KEY = "bookly.tokens";

const listeners = new Set<() => void>();

/** Cached so getSnapshot returns a stable reference; a fresh object each call loops forever. */
let cachedRaw: string | null = null;
let cachedTokens: TokenPair | null = null;

function readTokens(): TokenPair | null {
  let raw: string | null = null;
  try {
    raw = window.localStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
  if (raw !== cachedRaw) {
    cachedRaw = raw;
    try {
      cachedTokens = raw ? (JSON.parse(raw) as TokenPair) : null;
    } catch {
      // Corrupt storage: start signed out rather than crash on boot.
      cachedTokens = null;
    }
  }
  return cachedTokens;
}

function notify() {
  listeners.forEach((listener) => listener());
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  // Signing out in one tab signs out the others.
  window.addEventListener("storage", listener);
  return () => {
    listeners.delete(listener);
    window.removeEventListener("storage", listener);
  };
}

type AuthState = {
  tokens: TokenPair | null;
  /** False during server render and first paint; screens must not redirect before it is true. */
  ready: boolean;
  signIn: (tokens: TokenPair) => void;
  signOut: () => void;
};

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const tokens = useSyncExternalStore(subscribe, readTokens, () => null);
  const ready = useSyncExternalStore(
    subscribe,
    () => true,
    () => false,
  );

  const signIn = useCallback((next: TokenPair) => {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
    notify();
  }, []);

  const signOut = useCallback(() => {
    const current = readTokens();
    window.localStorage.removeItem(STORAGE_KEY);
    notify();
    // Best effort: the local session is already gone, so a failure here must not
    // strand the user on a page they can no longer use.
    if (current) {
      void api.logout(current.refreshToken).catch(() => undefined);
    }
  }, []);

  const value = useMemo(
    () => ({ tokens, ready, signIn, signOut }),
    [tokens, ready, signIn, signOut],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used inside AuthProvider");
  }
  return context;
}
