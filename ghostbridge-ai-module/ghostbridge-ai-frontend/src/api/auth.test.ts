import { afterEach, describe, expect, it, vi } from 'vitest';
import { bearerAuth, getAuthStrategy, noAuth, sessionCookieAuth, setAuthStrategy } from './auth';
import { requestJson } from './client';
import { jsonResponse, stubFetch } from '../test/http';

afterEach(() => {
  setAuthStrategy(null);
  vi.unstubAllGlobals();
});

describe('auth strategies', () => {
  it('sends no credentials by default', async () => {
    const stub = stubFetch(() => jsonResponse(200, {}));
    await requestJson({ method: 'GET', path: '/p' });
    expect(getAuthStrategy()).toBe(noAuth);
    expect(stub.mock.calls[0][1].headers).not.toHaveProperty('Authorization');
  });

  it('bearer strategy takes the token from memory per request and omits cookies', async () => {
    let token: string | undefined = 'in-memory-token';
    setAuthStrategy(bearerAuth(() => token));
    const stub = stubFetch(() => jsonResponse(200, {}));
    await requestJson({ method: 'GET', path: '/p' });
    token = undefined;
    await requestJson({ method: 'GET', path: '/p' });
    expect(stub.mock.calls[0][1].headers).toMatchObject({ Authorization: 'Bearer in-memory-token' });
    expect(stub.mock.calls[0][1].credentials).toBe('omit');
    expect(stub.mock.calls[1][1].headers).not.toHaveProperty('Authorization');
  });

  it('session strategy adds the CSRF header to unsafe methods only and never an Authorization header', async () => {
    setAuthStrategy(sessionCookieAuth({ csrfHeaderName: 'X-CSRF-TOKEN', getCsrfToken: () => 'csrf-1' }));
    const stub = stubFetch(() => jsonResponse(200, {}));
    await requestJson({ method: 'GET', path: '/p' });
    await requestJson({ method: 'POST', path: '/p', body: {} });
    expect(stub.mock.calls[0][1].headers).not.toHaveProperty('X-CSRF-TOKEN');
    expect(stub.mock.calls[1][1].headers).toMatchObject({ 'X-CSRF-TOKEN': 'csrf-1' });
    expect(stub.mock.calls[1][1].headers).not.toHaveProperty('Authorization');
    expect(stub.mock.calls[1][1].credentials).toBe('same-origin');
  });

  it('401 is surfaced, not bypassed', async () => {
    stubFetch(() => jsonResponse(401, { code: 'AUTHENTICATION_REQUIRED' }));
    await expect(requestJson({ method: 'GET', path: '/p' })).rejects.toMatchObject({ kind: 'unauthenticated' });
  });

  it('does not touch web storage', async () => {
    const setItem = vi.spyOn(Storage.prototype, 'setItem');
    setAuthStrategy(bearerAuth(() => 't'));
    stubFetch(() => jsonResponse(200, {}));
    await requestJson({ method: 'GET', path: '/p' });
    expect(setItem).not.toHaveBeenCalled();
    setItem.mockRestore();
  });
});
