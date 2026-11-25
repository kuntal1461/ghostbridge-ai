import { render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

/** Loads the real modules fresh under a given environment, as the bundler would at start-up. */
async function load(env: Record<string, string>) {
  vi.resetModules();
  for (const [key, value] of Object.entries(env)) vi.stubEnv(key, value);
  const fetchSpy = vi.fn(() => Promise.reject(new TypeError('real network must not be used')));
  vi.stubGlobal('fetch', fetchSpy);
  return { fetchSpy, api: await import('../aiApi'), banner: await import('../../components/feedback/MockBanner'), env: await import('../../config/env') };
}

beforeEach(() => vi.unstubAllEnvs());
afterEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
});

describe('development mock mode (explicitly enabled)', () => {
  it('serves labelled synthetic data through the real client without touching the network', async () => {
    const { api, fetchSpy, banner, env } = await load({ VITE_AI_MOCK: 'true' });
    expect(env.MOCK_ACTIVE).toBe(true);
    const response = await api.generate({ promptName: 'generic-structured', variables: { text: 'hello' } });
    expect(response.model).toContain('mock');
    expect(response.text).toContain('[MOCK]');
    expect((await api.getProviderStatus()).provider).toBe('mock');
    expect(fetchSpy).not.toHaveBeenCalled();
    render(<banner.MockBanner />);
    expect(screen.getByRole('status')).toHaveTextContent(/synthetic/i);
  });

  it('still surfaces simulated errors (401, 429 with Retry-After) as real errors', async () => {
    const { api } = await load({ VITE_AI_MOCK: 'true' });
    await expect(api.generate({ promptName: 'p', variables: { text: '!mock-401' } })).rejects.toMatchObject({ kind: 'unauthenticated' });
    await expect(api.generate({ promptName: 'p', variables: { text: '!mock-429' } })).rejects.toMatchObject({ kind: 'rate-limited', retryAfterSeconds: 7 });
  });
});

describe('without the explicit flag', () => {
  it('uses the real network path and shows no banner, so a failure is a failure and never a mock success', async () => {
    const { api, fetchSpy, banner, env } = await load({ VITE_AI_MOCK: 'false' });
    expect(env.MOCK_ACTIVE).toBe(false);
    await expect(api.getUsage()).rejects.toMatchObject({ kind: 'network' });
    expect(fetchSpy).toHaveBeenCalledTimes(1);
    const { container } = render(<banner.MockBanner />);
    expect(container).toBeEmptyDOMElement();
  });
});
