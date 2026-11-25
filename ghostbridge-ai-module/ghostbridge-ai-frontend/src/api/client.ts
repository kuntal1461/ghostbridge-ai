import { config } from '../config/env';
import { getAuthStrategy, notifyUnauthenticated } from './auth';
import { ApiError, kindForStatus, type FieldError } from './errors';
import { resolveFetch } from './transport';

export interface RequestSpec {
  method: 'GET' | 'POST';
  /** Path below the API base, for example /api/v1/ai/usage. */
  path: string;
  body?: unknown;
  /** Alternative to {@code body}: sent as application/x-www-form-urlencoded (used for the browser sign-in). */
  form?: Record<string, string>;
  /** Caller cancellation (component unmount, reset button). */
  signal?: AbortSignal;
  timeoutMs?: number;
}

const MAX_ERROR_BODY_CHARS = 65_536;
const MAX_DETAIL_CHARS = 300;
const MAX_FIELD_ERRORS = 20;
const MAX_RETRY_AFTER_SECONDS = 86_400;

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function text(value: unknown, max = MAX_DETAIL_CHARS): string | undefined {
  return typeof value === 'string' && value.length > 0 ? value.slice(0, max) : undefined;
}

/** Retry-After is accepted only as a plain non-negative integer number of seconds; anything else is "unknown". */
export function parseRetryAfter(header: string | null): number | undefined {
  if (header === null || !/^\d{1,6}$/.test(header.trim())) return undefined;
  const seconds = Number(header.trim());
  return seconds <= MAX_RETRY_AFTER_SECONDS ? seconds : undefined;
}

function fieldErrors(value: unknown): FieldError[] {
  if (!Array.isArray(value)) return [];
  return value
    .slice(0, MAX_FIELD_ERRORS)
    .filter(isRecord)
    .map((entry) => ({ field: text(entry.field, 100) ?? 'request', message: text(entry.message) ?? 'Invalid value' }));
}

async function errorFrom(response: Response): Promise<ApiError> {
  const kind = kindForStatus(response.status);
  let problem: Record<string, unknown> = {};
  try {
    const raw = (await response.text()).slice(0, MAX_ERROR_BODY_CHARS);
    const parsed: unknown = JSON.parse(raw);
    if (isRecord(parsed)) problem = parsed;
  } catch {
    // Non-JSON error body (for example an HTML gateway page): ignored, never rendered.
  }
  const serverError = response.status >= 500;
  return new ApiError(kind, {
    status: response.status,
    code: text(problem.code, 64),
    reason: text(problem.reason, 64),
    correlationId: text(problem.correlationId, 64),
    // 5xx text is never shown: provider detail must not reach the screen even if a server bug includes it.
    detail: serverError ? undefined : text(problem.detail),
    fieldErrors: serverError ? [] : fieldErrors(problem.errors),
    retryAfterSeconds: response.status === 429 ? parseRetryAfter(response.headers.get('Retry-After')) : undefined,
  });
}

/** Combines the caller's signal with a timeout. Reports which of the two ended the request. */
function withTimeout(caller: AbortSignal | undefined, timeoutMs: number) {
  const controller = new AbortController();
  let timedOut = false;
  const timer = setTimeout(() => {
    timedOut = true;
    controller.abort();
  }, timeoutMs);
  const onCallerAbort = () => controller.abort();
  if (caller?.aborted) controller.abort();
  caller?.addEventListener('abort', onCallerAbort, { once: true });
  return {
    signal: controller.signal,
    didTimeOut: () => timedOut,
    didCallerAbort: () => caller?.aborted === true,
    dispose: () => {
      clearTimeout(timer);
      caller?.removeEventListener('abort', onCallerAbort);
    },
  };
}

function encodeBody(spec: RequestSpec): string | undefined {
  if (spec.form !== undefined) return new URLSearchParams(spec.form).toString();
  return spec.body === undefined ? undefined : JSON.stringify(spec.body);
}

/** The single HTTP entry point of the frontend. Never retries. Returns the parsed JSON body (untyped: see guards.ts). */
export async function requestJson(spec: RequestSpec): Promise<unknown> {
  const auth = getAuthStrategy();
  const headers: Record<string, string> = { Accept: 'application/json', ...auth.headers(spec.method) };
  if (spec.body !== undefined) headers['Content-Type'] = 'application/json';
  if (spec.form !== undefined) headers['Content-Type'] = 'application/x-www-form-urlencoded';

  const guard = withTimeout(spec.signal, spec.timeoutMs ?? config.timeoutMs);
  try {
    const fetchImpl = await resolveFetch();
    const response = await fetchImpl(`${config.apiBaseUrl}${spec.path}`, {
      method: spec.method,
      headers,
      body: encodeBody(spec),
      credentials: auth.credentials,
      cache: 'no-store',
      referrerPolicy: 'no-referrer',
      signal: guard.signal,
    });
    if (!response.ok) {
      if (response.status === 401) notifyUnauthenticated();
      throw await errorFrom(response);
    }
    if (response.status === 204) return undefined;
    try {
      return (await response.json()) as unknown;
    } catch (error) {
      if (guard.signal.aborted) throw error;
      throw new ApiError('invalid-response', { status: response.status });
    }
  } catch (error) {
    if (error instanceof ApiError) throw error;
    if (guard.didTimeOut()) throw new ApiError('timeout');
    if (guard.didCallerAbort()) throw new ApiError('aborted');
    throw new ApiError('network');
  } finally {
    guard.dispose();
  }
}
