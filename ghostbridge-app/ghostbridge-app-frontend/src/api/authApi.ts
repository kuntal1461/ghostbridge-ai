import { ApiError, requestJson, sessionCookieAuth, setAiAuthStrategy } from 'ghostbridge-ai-frontend';

/** Browser-facing sign-in API of GhostBridge App. All HTTP goes through the one shared client of the AI frontend. */

export interface AuthUser {
  username: string;
  roles: string[];
}

interface CsrfToken {
  headerName: string;
  token: string;
}

const BASE = '/api/v1/auth';
let csrf: CsrfToken | null = null;

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function parseUser(value: unknown): AuthUser {
  if (!isRecord(value) || typeof value.username !== 'string' || !Array.isArray(value.roles) || value.roles.some((r) => typeof r !== 'string')) {
    throw new ApiError('invalid-response');
  }
  return { username: value.username, roles: value.roles as string[] };
}

function parseCsrf(value: unknown): CsrfToken {
  if (!isRecord(value) || typeof value.headerName !== 'string' || typeof value.token !== 'string') throw new ApiError('invalid-response');
  return { headerName: value.headerName, token: value.token };
}

/**
 * Fetches a fresh CSRF token and installs the session-cookie strategy that sends it on every state-changing request.
 * The token lives in memory only. It must be refreshed after sign-in and sign-out, because the server rotates it.
 */
export async function refreshCsrf(signal?: AbortSignal): Promise<void> {
  const fresh = parseCsrf(await requestJson({ method: 'GET', path: `${BASE}/csrf`, signal }));
  csrf = fresh;
  setAiAuthStrategy(sessionCookieAuth({ csrfHeaderName: fresh.headerName, getCsrfToken: () => csrf?.token }));
}

/** The signed-in user, or null when there is no valid session (401 is the normal "signed out" answer). */
export async function fetchCurrentUser(signal?: AbortSignal): Promise<AuthUser | null> {
  try {
    return parseUser(await requestJson({ method: 'GET', path: `${BASE}/me`, signal }));
  } catch (error) {
    if (error instanceof ApiError && error.kind === 'unauthenticated') return null;
    throw error;
  }
}

export async function signIn(username: string, password: string): Promise<AuthUser> {
  await refreshCsrf(); // a token from an expired session would be rejected: always start from a fresh one
  const user = parseUser(await requestJson({ method: 'POST', path: `${BASE}/login`, form: { username, password } }));
  try {
    await refreshCsrf();
  } catch {
    csrf = null; // signed in already; the next state-changing call fetches the token again
  }
  return user;
}

export async function signOut(): Promise<void> {
  try {
    await requestJson({ method: 'POST', path: `${BASE}/logout` });
  } finally {
    csrf = null;
    await refreshCsrf();
  }
}

/** Test seam: forget the token held in memory. */
export function resetAuthApiForTests(): void {
  csrf = null;
}
