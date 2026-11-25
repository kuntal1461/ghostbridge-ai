/**
 * Authentication hook. The host application (ghostbridge-app-frontend) installs the strategy after sign-in; this module only
 * defines how credentials are attached. Nothing here reads or writes any browser storage,
 * and there is no default token: out of the box requests carry no credentials and the backend answers 401.
 *
 * Bearer tokens and session cookies are different strategies with different risks and are never interchangeable:
 *  - bearer: the app supplies the token from memory; the browser never attaches it on its own, so CSRF does not apply.
 *  - session cookie: the browser attaches the cookie automatically, so unsafe requests MUST carry the CSRF token header
 *    (Spring CSRF is active when ghostbridge.security.stateless=false). Cross-origin cookies additionally require
 *    credentials 'include' plus a matching CORS allow-credentials configuration on the backend.
 */

export interface AuthStrategy {
  /** Extra request headers for this method. Must not contain secrets that are persisted anywhere. */
  headers(method: string): Record<string, string>;
  readonly credentials: RequestCredentials;
}

const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS']);

export const noAuth: AuthStrategy = {
  headers: () => ({}),
  credentials: 'same-origin',
};

export function bearerAuth(getToken: () => string | undefined): AuthStrategy {
  return {
    credentials: 'omit',
    headers: (): Record<string, string> => {
      const token = getToken();
      return token ? { Authorization: `Bearer ${token}` } : {};
    },
  };
}

export interface SessionCookieOptions {
  /** Name of the CSRF header the backend expects (for example X-CSRF-TOKEN or X-XSRF-TOKEN). Required: no guessing. */
  csrfHeaderName: string;
  getCsrfToken: () => string | undefined;
  /** Use 'include' only for a cross-origin API that allows credentials. */
  credentials?: 'same-origin' | 'include';
}

export function sessionCookieAuth(options: SessionCookieOptions): AuthStrategy {
  return {
    credentials: options.credentials ?? 'same-origin',
    headers: (method) => {
      if (SAFE_METHODS.has(method.toUpperCase())) return {};
      const value = options.getCsrfToken();
      return value ? { [options.csrfHeaderName]: value } : {};
    },
  };
}

let current: AuthStrategy = noAuth;
let unauthenticatedHandler: (() => void) | null = null;

/** Called once by the host application. Pass null to reset to "no credentials". */
export function setAuthStrategy(strategy: AuthStrategy | null): void {
  current = strategy ?? noAuth;
}

export function getAuthStrategy(): AuthStrategy {
  return current;
}

/**
 * The host registers one callback that runs whenever the backend answers 401 (for example an expired session), so it can
 * return to its sign-in screen. The 401 is still reported to the caller; nothing is retried or bypassed.
 */
export function setUnauthenticatedHandler(handler: (() => void) | null): void {
  unauthenticatedHandler = handler;
}

export function notifyUnauthenticated(): void {
  unauthenticatedHandler?.();
}
