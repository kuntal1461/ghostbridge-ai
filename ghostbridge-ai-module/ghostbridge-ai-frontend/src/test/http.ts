import { vi } from 'vitest';

export function jsonResponse(status: number, body: unknown, headers: Record<string, string> = {}): Response {
  const contentType = status >= 400 ? 'application/problem+json' : 'application/json';
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': contentType, ...headers } });
}

export function htmlResponse(status: number): Response {
  return new Response('<html><body>Bad Gateway <script>alert(1)</script></body></html>', {
    status,
    headers: { 'Content-Type': 'text/html' },
  });
}

/** Replaces global fetch with a stub. Returns the stub so tests can inspect calls. */
export function stubFetch(handler: (input: string, init: RequestInit) => Response | Promise<Response>) {
  const stub = vi.fn((input: string, init: RequestInit) => Promise.resolve(handler(input, init)));
  vi.stubGlobal('fetch', stub);
  return stub;
}

export const GENERATE_OK = {
  requestId: 'req-1',
  model: 'test-model',
  text: 'Hello from the model',
  durationMs: 120,
  usage: { inputTokens: 10, outputTokens: 20, totalTokens: 30 },
};

export const STRUCTURED_OK = {
  requestId: 'req-2',
  schemaName: 'generic-summary',
  model: 'test-model',
  structured: { summary: 'Short summary' },
  durationMs: 90,
};

export const STATUS_OK = {
  configured: true,
  status: 'AVAILABLE',
  provider: 'ollama',
  model: 'test-model',
  capabilities: ['TEXT_GENERATION', 'STRUCTURED_OUTPUT'],
};

export const USAGE_OK = {
  scope: 'RETAINED_HISTORY',
  historyLimit: 500,
  requestCount: 12,
  successCount: 10,
  failureCount: 2,
  requestsWithTokenUsage: 9,
  inputTokens: 1200,
  outputTokens: 3400,
  totalTokens: 4600,
  rateLimit: { limitPerMinute: 20, remaining: 18, resetInSeconds: 41, maxConcurrent: 2 },
};
