import { afterEach, describe, expect, it, vi } from 'vitest';
import { setUnauthenticatedHandler } from './auth';
import { requestJson, parseRetryAfter } from './client';
import { ApiError } from './errors';
import { htmlResponse, jsonResponse, stubFetch } from '../test/http';

afterEach(() => {
  vi.unstubAllGlobals();
  vi.useRealTimers();
});

const call = () => requestJson({ method: 'GET', path: '/api/v1/ai/usage' });

async function failure(promise: Promise<unknown>): Promise<ApiError> {
  try {
    await promise;
  } catch (error) {
    expect(error).toBeInstanceOf(ApiError);
    return error as ApiError;
  }
  throw new Error('expected the request to fail');
}

describe('requestJson status mapping', () => {
  it.each([
    [400, 'validation'],
    [401, 'unauthenticated'],
    [403, 'forbidden'],
    [413, 'payload-too-large'],
    [429, 'rate-limited'],
    [502, 'bad-gateway'],
    [503, 'unavailable'],
    [504, 'gateway-timeout'],
    [404, 'http'],
    [500, 'http'],
  ])('maps HTTP %i to %s', async (status, kind) => {
    stubFetch(() => jsonResponse(status, { code: 'X', detail: 'details' }));
    const error = await failure(call());
    expect(error.kind).toBe(kind);
    expect(error.status).toBe(status);
  });

  it('reads field errors and correlation id from a 400 problem body', async () => {
    stubFetch(() =>
      jsonResponse(400, {
        code: 'VALIDATION_ERROR',
        detail: 'Request validation failed',
        correlationId: 'abc-123',
        errors: [{ field: 'promptName', message: 'must not be blank' }],
      }),
    );
    const error = await failure(call());
    expect(error.fieldErrors).toEqual([{ field: 'promptName', message: 'must not be blank' }]);
    expect(error.correlationId).toBe('abc-123');
    expect(error.detail).toBe('Request validation failed');
  });

  it('never keeps server detail for 5xx responses', async () => {
    stubFetch(() => jsonResponse(502, { code: 'PROVIDER_FAILURE', detail: 'upstream secret detail', reason: 'PROVIDER_FAILURE' }));
    const error = await failure(call());
    expect(error.detail).toBeUndefined();
    expect(error.message).not.toContain('secret');
  });

  it('handles a non-JSON error body without crashing or exposing it', async () => {
    stubFetch(() => htmlResponse(502));
    const error = await failure(call());
    expect(error.kind).toBe('bad-gateway');
    expect(error.detail).toBeUndefined();
    expect(error.message).not.toContain('<script>');
  });

  it('maps a network failure to a generic error without raw text', async () => {
    stubFetch(() => {
      throw new TypeError('Failed to fetch https://internal.example/secret');
    });
    const error = await failure(call());
    expect(error.kind).toBe('network');
    expect(error.message).not.toContain('internal.example');
  });

  it('reports a 2xx body that is not JSON as invalid-response', async () => {
    stubFetch(() => new Response('not json', { status: 200 }));
    expect((await failure(call())).kind).toBe('invalid-response');
  });

  it('returns parsed JSON on success and sends JSON headers without credentials', async () => {
    const stub = stubFetch(() => jsonResponse(200, { ok: true }));
    await expect(requestJson({ method: 'POST', path: '/p', body: { a: 1 } })).resolves.toEqual({ ok: true });
    const [url, init] = stub.mock.calls[0];
    expect(url).toBe('/p');
    expect(init.method).toBe('POST');
    expect(init.body).toBe('{"a":1}');
    expect(init.headers).toMatchObject({ Accept: 'application/json', 'Content-Type': 'application/json' });
    expect(init.headers).not.toHaveProperty('Authorization');
    expect(init.credentials).toBe('same-origin');
    expect(init.cache).toBe('no-store');
  });
});

describe('Retry-After', () => {
  it('is read as whole seconds on 429', async () => {
    stubFetch(() => jsonResponse(429, { code: 'RATE_LIMITED' }, { 'Retry-After': '12' }));
    expect((await failure(call())).retryAfterSeconds).toBe(12);
  });

  it('is undefined when missing, so no timing is invented', async () => {
    stubFetch(() => jsonResponse(429, { code: 'RATE_LIMITED' }));
    expect((await failure(call())).retryAfterSeconds).toBeUndefined();
  });

  it.each([['abc'], ['-5'], ['1.5'], [''], ['Wed, 21 Oct 2026 07:28:00 GMT'], ['99999999']])('rejects %j', (value) => {
    expect(parseRetryAfter(value)).toBeUndefined();
  });

  it('does not retry automatically', async () => {
    const stub = stubFetch(() => jsonResponse(429, {}, { 'Retry-After': '1' }));
    await failure(call());
    expect(stub).toHaveBeenCalledTimes(1);
  });
});

describe('cancellation and timeout', () => {
  it('reports a caller abort as aborted', async () => {
    stubFetch(() => {
      throw new DOMException('Aborted', 'AbortError');
    });
    const controller = new AbortController();
    controller.abort();
    expect((await failure(requestJson({ method: 'GET', path: '/p', signal: controller.signal }))).kind).toBe('aborted');
  });

  it('aborts the underlying fetch when the caller aborts mid-flight', async () => {
    let seen: AbortSignal | undefined;
    stubFetch(
      (_url, init) => {
        seen = init.signal ?? undefined;
        return new Promise<Response>(() => undefined) as unknown as Response;
      },
    );
    const controller = new AbortController();
    void requestJson({ method: 'GET', path: '/p', signal: controller.signal }).catch(() => undefined);
    await Promise.resolve();
    await Promise.resolve();
    controller.abort();
    expect(seen?.aborted).toBe(true);
  });

  it('times out with its own kind', async () => {
    vi.useFakeTimers();
    vi.stubGlobal(
      'fetch',
      vi.fn((_url: string, init: RequestInit) => new Promise((_resolve, reject) => {
        init.signal?.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')));
      })),
    );
    const pending = failure(requestJson({ method: 'GET', path: '/p', timeoutMs: 1000 }));
    await vi.advanceTimersByTimeAsync(1001);
    expect((await pending).kind).toBe('timeout');
  });
});

describe('form bodies, no-content answers and the unauthenticated hook', () => {
  it('sends a form as urlencoded text without a JSON body', async () => {
    const stub = stubFetch(() => jsonResponse(200, {}));
    await requestJson({ method: 'POST', path: '/login', form: { username: 'a b', password: 'p&q=1' } });
    const init = stub.mock.calls[0][1];
    expect(init.body).toBe('username=a+b&password=p%26q%3D1');
    expect(init.headers).toMatchObject({ 'Content-Type': 'application/x-www-form-urlencoded' });
  });

  it('returns undefined for 204 instead of failing to parse', async () => {
    stubFetch(() => new Response(null, { status: 204 }));
    await expect(requestJson({ method: 'POST', path: '/logout' })).resolves.toBeUndefined();
  });

  it('notifies the registered handler on 401 and still reports the error', async () => {
    const handler = vi.fn();
    setUnauthenticatedHandler(handler);
    stubFetch(() => jsonResponse(401, { code: 'AUTHENTICATION_REQUIRED' }));
    expect((await failure(call())).kind).toBe('unauthenticated');
    expect(handler).toHaveBeenCalledTimes(1);
    stubFetch(() => jsonResponse(403, {}));
    await failure(call());
    expect(handler).toHaveBeenCalledTimes(1);
    setUnauthenticatedHandler(null);
  });
});
