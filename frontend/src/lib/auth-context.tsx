"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useSyncExternalStore,
} from "react";
import { api, setRefreshHandler, type TokenPair } from "./api";

/**
 * Session state for the browser.
 *
 * <p>Tokens are held in localStorage so a reload does not log the user out. That places them within
 * reach of any script running on this origin, which is a real trade-off: an httpOnly cookie would
 * resist XSS better, but it would also make the API browser-shaped, and the same endpoints have to
 * serve the Android and iOS clients that come later.
 *
 * <p>Be precise about what that costs, because the first version of this comment was not. Both
 * tokens live under one key, so a single script read yields both — the 15-minute access-token
 * lifetime bounds nothing an attacker gains. What remains is refresh rotation with reuse detection,
 * and that only works if this client actually rotates, which is why {@link setRefreshHandler} is
 * wired below: without it the browser never presented a spent token and detection could never fire.
 * The refresh lifetime is seven days rather than thirty for the same reason. Recorded in the turn-1
 * audit as an accepted limitation, with the residual risk stated rather than waved at.
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
      cachedTokens = raw ? asTokenPair(JSON.parse(raw)) : null;
    } catch {
      // Corrupt storage: start signed out rather than crash on boot.
      cachedTokens = null;
    }
  }
  return cachedTokens;
}

/**
 * Accepts stored state only if it can actually authenticate a request.
 *
 * <p>The presence of the key is not the same as having a session. An entry holding no access
 * token — a partial write, an interrupted logout, hand-edited storage — used to count as signed in,
 * which left the visitor on a dashboard that could never load data and gave them no route to the
 * login form that would fix it. Not an authentication bypass, since the server still refuses every
 * request; a dead end, which is its own kind of failure.
 */
function asTokenPair(value: unknown): TokenPair | null {
  if (typeof value !== "object" || value === null) {
    return null;
  }
  const candidate = value as Partial<TokenPair>;
  const usable =
    typeof candidate.accessToken === "string" &&
    candidate.accessToken.length > 0 &&
    typeof candidate.refreshToken === "string" &&
    candidate.refreshToken.length > 0;
  return usable ? (candidate as TokenPair) : null;
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

  // Rotation on a 401, so the server's reuse detection has something to detect.
  useEffect(() => {
    setRefreshHandler(async () => {
      const current = readTokens();
      if (!current) {
        return null;
      }
      try {
        const renewed = await api.refresh(current.refreshToken);
        window.localStorage.setItem(STORAGE_KEY, JSON.stringify(renewed));
        notify();
        return renewed.accessToken;
      } catch {
        // The refresh token is spent, expired, or its family was revoked because someone
        // replayed it. Either way this session is over; show the login screen.
        window.localStorage.removeItem(STORAGE_KEY);
        notify();
        return null;
      }
    });
    return () => setRefreshHandler(null);
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
