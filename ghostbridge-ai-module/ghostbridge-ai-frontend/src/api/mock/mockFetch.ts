/**
 * DEVELOPMENT-ONLY synthetic backend. It is imported only behind the literal `import.meta.env.DEV && config.mockEnabled` condition in transport.ts and is absent
 * from production bundles (checked by scripts/check-dist.mjs). All data is invented and labelled as such. It is a
 * stand-in for an unavailable authenticated backend, never a fallback for real failures.
 *
 * Simulate errors by sending the text `!mock-401`, `!mock-403`, `!mock-413`, `!mock-429`, `!mock-502`, `!mock-503`,
 * `!mock-504` or `!mock-slow` in any variable value.
 */
import type { FetchLike } from '../transport';

export const MOCK_LABEL = 'MOCK DATA (synthetic)';
const MOCK_MODEL = 'mock-model (synthetic)';

const counters = { requests: 0, successes: 0, failures: 0, input: 0, output: 0 };

function reply(status: number, body: unknown, headers: Record<string, string> = {}): Response {
  const contentType = status >= 400 ? 'application/problem+json' : 'application/json';
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': contentType, 'X-GhostBridge-Mock': 'true', ...headers },
  });
}

function problem(status: number, code: string, detail: string, extra: Record<string, unknown> = {}): Response {
  return reply(status, { status, code, detail, correlationId: 'mock-correlation', ...extra }, extra.retry ? { 'Retry-After': '7' } : {});
}

const SIMULATED: Record<string, () => Response> = {
  '!mock-401': () => problem(401, 'AUTHENTICATION_REQUIRED', 'Authentication is required'),
  '!mock-403': () => problem(403, 'ACCESS_DENIED', 'Access is denied'),
  '!mock-413': () => problem(413, 'PAYLOAD_TOO_LARGE', 'Request is too large'),
  '!mock-429': () => problem(429, 'RATE_LIMITED', 'Too many requests', { reason: 'RATE_LIMITED', retry: true }),
  '!mock-502': () => problem(502, 'PROVIDER_FAILURE', 'mock provider detail that must never be shown'),
  '!mock-503': () => problem(503, 'PROVIDER_UNAVAILABLE', 'AI provider is unavailable'),
  '!mock-504': () => problem(504, 'PROVIDER_TIMEOUT', 'mock timeout detail'),
};

function simulatedFailure(body: Record<string, unknown>): Response | undefined {
  const variables = (body.variables ?? {}) as Record<string, unknown>;
  for (const value of Object.values(variables)) {
    if (typeof value === 'string') {
      const key = Object.keys(SIMULATED).find((token) => value.includes(token));
      if (key) return SIMULATED[key]();
    }
  }
  return undefined;
}

function delay(ms: number, signal: AbortSignal | null | undefined): Promise<void> {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(resolve, ms);
    signal?.addEventListener('abort', () => {
      clearTimeout(timer);
      reject(new DOMException('Aborted', 'AbortError'));
    }, { once: true });
  });
}

function usageFor(text: string) {
  const inputTokens = Math.max(1, Math.ceil(text.length / 4));
  const outputTokens = 42;
  counters.input += inputTokens;
  counters.output += outputTokens;
  return { inputTokens, outputTokens, totalTokens: inputTokens + outputTokens };
}

function sampleText(body: Record<string, unknown>): string {
  const variables = (body.variables ?? {}) as Record<string, unknown>;
  return Object.values(variables).filter((v): v is string => typeof v === 'string').join(' ');
}

function summary(input: string) {
  return {
    // Matches the real generic-summary schema (only "summary"); the markup shows that model text is never interpreted.
    summary: `Synthetic summary of ${input.length} characters of input. <img src=x onerror=alert(1)> is shown as text.`,
  };
}

async function handlePost(path: string, init: RequestInit): Promise<Response> {
  counters.requests += 1;
  const body = JSON.parse(String(init.body ?? '{}')) as Record<string, unknown>;
  const failure = simulatedFailure(body);
  if (failure) {
    counters.failures += 1;
    return failure;
  }
  if (sampleText(body).includes('!mock-slow')) await delay(5_000, init.signal);
  counters.successes += 1;
  const input = sampleText(body);
  const base = { requestId: `mock-${counters.requests}`, model: MOCK_MODEL, usage: usageFor(input), durationMs: 120 };
  if (path.endsWith('/generate/structured')) {
    return reply(200, { ...base, schemaName: String(body.schemaName), structured: summary(input) });
  }
  return reply(200, { ...base, text: `[MOCK] Synthetic model output. Input was ${input.length} characters.`, structured: summary(input) });
}

function handleGet(path: string): Response {
  if (path.endsWith('/provider/status')) {
    return reply(200, { configured: true, status: 'AVAILABLE', provider: 'mock', model: MOCK_MODEL, capabilities: ['TEXT_GENERATION', 'STRUCTURED_OUTPUT'] });
  }
  return reply(200, {
    scope: 'RETAINED_HISTORY',
    historyLimit: 500,
    requestCount: counters.requests,
    successCount: counters.successes,
    failureCount: counters.failures,
    requestsWithTokenUsage: counters.successes,
    inputTokens: counters.input,
    outputTokens: counters.output,
    totalTokens: counters.input + counters.output,
    rateLimit: { limitPerMinute: 20, remaining: Math.max(0, 20 - counters.requests), resetInSeconds: 30, maxConcurrent: 2 },
  });
}

export const mockFetch: FetchLike = async (input, init = {}) => {
  if (init.signal?.aborted) throw new DOMException('Aborted', 'AbortError');
  const path = new URL(input, 'http://mock.invalid').pathname;
  if (init.method === 'POST' && (path.endsWith('/generate') || path.endsWith('/generate/structured'))) return handlePost(path, init);
  if (init.method === 'GET' && (path.endsWith('/provider/status') || path.endsWith('/usage'))) return handleGet(path);
  return problem(404, 'NOT_FOUND', 'Not found');
};
