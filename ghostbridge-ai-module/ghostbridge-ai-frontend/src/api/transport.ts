import { config } from '../config/env';

export type FetchLike = (input: string, init?: RequestInit) => Promise<Response>;

/**
 * The only place that decides which `fetch` is used. `import.meta.env.DEV` must stay written literally in this
 * condition: Vite replaces it with `false` in production builds, which removes the dynamic import and the whole mock
 * module from the bundle (an indirection through another constant is NOT folded; scripts/check-dist.mjs enforces this).
 * A failed real request is never retried against the mock.
 */
export async function resolveFetch(): Promise<FetchLike> {
  if (import.meta.env.DEV && config.mockEnabled) {
    const { mockFetch } = await import('./mock/mockFetch');
    return mockFetch;
  }
  return (input, init) => globalThis.fetch(input, init);
}
