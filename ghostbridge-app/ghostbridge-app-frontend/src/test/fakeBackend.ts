import { vi } from 'vitest';

export interface Call {
  method: string;
  path: string;
  headers: Record<string, string>;
  body: string | undefined;
}

interface Options {
  /** Start with a valid session. */
  signedIn?: boolean;
  /** Status of POST /login when the password is wrong (default 401). */
  failureStatus?: number;
  failureHeaders?: Record<string, string>;
}

const json = (status: number, body: unknown, headers: Record<string, string> = {}) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': status >= 400 ? 'application/problem+json' : 'application/json', ...headers },
  });

export const PASSWORD = 'test-pass-12';

/** A small stateful stand-in for GhostBridge App: csrf, sign-in, sign-out, me and the four AI endpoints. */
export function installFakeBackend(options: Options = {}) {
  let signedIn = options.signedIn ?? false;
  let token = 'csrf-1';
  let tokens = 1;
  const calls: Call[] = [];

  const stub = vi.fn((input: string, init: RequestInit = {}) => {
    const method = init.method ?? 'GET';
    const headers = (init.headers ?? {}) as Record<string, string>;
    const body = typeof init.body === 'string' ? init.body : undefined;
    calls.push({ method, path: input, headers, body });
    const csrfOk = headers['X-CSRF-TOKEN'] === token;

    if (input === '/api/v1/auth/csrf') {
      tokens += 1;
      token = `csrf-${tokens}`;
      return Promise.resolve(json(200, { headerName: 'X-CSRF-TOKEN', token }));
    }
    if (input === '/api/v1/auth/me') {
      return Promise.resolve(signedIn ? json(200, { username: 'alice', roles: ['AI_USER'] }) : json(401, { code: 'AUTHENTICATION_REQUIRED' }));
    }
    if (input === '/api/v1/auth/login') {
      if (!csrfOk) return Promise.resolve(json(403, { code: 'ACCESS_DENIED' }));
      const form = new URLSearchParams(body);
      if (form.get('password') !== PASSWORD) {
        return Promise.resolve(json(options.failureStatus ?? 401, { code: 'AUTHENTICATION_REQUIRED', detail: 'Invalid credentials' }, options.failureHeaders));
      }
      signedIn = true;
      return Promise.resolve(json(200, { username: form.get('username'), roles: ['AI_USER'] }));
    }
    if (input === '/api/v1/auth/logout') {
      if (!csrfOk) return Promise.resolve(json(403, { code: 'ACCESS_DENIED' }));
      signedIn = false;
      return Promise.resolve(new Response(null, { status: 204 }));
    }
    if (!signedIn) return Promise.resolve(json(401, { code: 'AUTHENTICATION_REQUIRED' }));
    if (input === '/api/v1/ai/provider/status') {
      return Promise.resolve(json(200, { configured: true, status: 'AVAILABLE', provider: 'ollama', model: 'phi3:3.8b', capabilities: ['TEXT_GENERATION'] }));
    }
    if (input === '/api/v1/ai/usage') {
      return Promise.resolve(json(200, {
        scope: 'RETAINED_HISTORY', historyLimit: 200, requestCount: 0, successCount: 0, failureCount: 0, requestsWithTokenUsage: 0,
        inputTokens: 0, outputTokens: 0, totalTokens: 0, rateLimit: { limitPerMinute: 10, remaining: 10, resetInSeconds: 60, maxConcurrent: 1 },
      }));
    }
    return Promise.resolve(json(404, { code: 'RESOURCE_NOT_FOUND' }));
  });
  vi.stubGlobal('fetch', stub);

  return {
    calls,
    stub,
    expireSession: () => {
      signedIn = false;
    },
  };
}
