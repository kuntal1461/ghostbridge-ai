import { describe, expect, it } from 'vitest';
import { MOCK_LABEL, mockFetch } from './mockFetch';

const post = (path: string, variables: Record<string, string>) =>
  mockFetch(path, { method: 'POST', body: JSON.stringify({ promptName: 'p', schemaName: 's', variables }) });

describe('development mock', () => {
  it('answers all four endpoints with labelled synthetic data', async () => {
    const generated = await post('/api/v1/ai/generate', { text: 'hello' });
    expect(generated.headers.get('X-GhostBridge-Mock')).toBe('true');
    const body = await generated.json();
    expect(body.model).toContain('mock');
    expect(body.text).toContain('[MOCK]');
    expect((await (await post('/api/v1/ai/generate/structured', { text: 'x' })).json()).structured.summary).toContain('Synthetic');
    expect((await (await mockFetch('/api/v1/ai/provider/status', { method: 'GET' })).json()).provider).toBe('mock');
    expect((await (await mockFetch('/api/v1/ai/usage', { method: 'GET' })).json()).scope).toBe('RETAINED_HISTORY');
    expect(MOCK_LABEL).toMatch(/MOCK/);
  });

  it.each([['!mock-401', 401], ['!mock-403', 403], ['!mock-413', 413], ['!mock-429', 429], ['!mock-503', 503]])(
    'simulates %s',
    async (token, status) => {
      const response = await post('/api/v1/ai/generate', { text: token });
      expect(response.status).toBe(status);
    },
  );

  it('sends Retry-After on the simulated 429', async () => {
    expect((await post('/api/v1/ai/generate', { text: '!mock-429' })).headers.get('Retry-After')).toBe('7');
  });

  it('rejects unknown routes', async () => {
    expect((await mockFetch('/api/v1/ai/other', { method: 'GET' })).status).toBe(404);
  });
});
