import { describe, expect, it } from 'vitest';
import { DEFAULT_SCHEMAS, DEFAULT_TIMEOUT_MS, MAX_TIMEOUT_MS, MIN_TIMEOUT_MS, readConfig } from './env';

describe('readConfig', () => {
  it('uses safe defaults', () => {
    expect(readConfig({})).toEqual({
      apiBaseUrl: '',
      timeoutMs: DEFAULT_TIMEOUT_MS,
      structuredSchemas: DEFAULT_SCHEMAS,
      mockEnabled: false,
    });
  });

  it('normalises the base URL and rejects unsafe ones', () => {
    expect(readConfig({ VITE_API_BASE_URL: 'https://api.example.com/' }).apiBaseUrl).toBe('https://api.example.com');
    expect(() => readConfig({ VITE_API_BASE_URL: 'javascript:alert(1)' })).toThrow();
    expect(() => readConfig({ VITE_API_BASE_URL: 'https://user:pass@api.example.com' })).toThrow();
    expect(() => readConfig({ VITE_API_BASE_URL: 'not a url' })).toThrow();
  });

  it('clamps the timeout and ignores garbage', () => {
    expect(readConfig({ VITE_API_TIMEOUT_MS: '5' }).timeoutMs).toBe(MIN_TIMEOUT_MS);
    expect(readConfig({ VITE_API_TIMEOUT_MS: '99999999' }).timeoutMs).toBe(MAX_TIMEOUT_MS);
    expect(readConfig({ VITE_API_TIMEOUT_MS: 'abc' }).timeoutMs).toBe(DEFAULT_TIMEOUT_MS);
  });

  it('keeps only valid, unique schema identifiers', () => {
    expect(readConfig({ VITE_AI_STRUCTURED_SCHEMAS: 'a-b, c ,a-b,BAD NAME,' }).structuredSchemas).toEqual(['a-b', 'c']);
    expect(readConfig({ VITE_AI_STRUCTURED_SCHEMAS: 'BAD NAME' }).structuredSchemas).toEqual(DEFAULT_SCHEMAS);
  });

  it('enables the mock only in a development build with the explicit flag', () => {
    expect(readConfig({ DEV: true, VITE_AI_MOCK: 'true' }).mockEnabled).toBe(true);
    expect(readConfig({ DEV: true, VITE_AI_MOCK: 'false' }).mockEnabled).toBe(false);
    expect(readConfig({ DEV: true }).mockEnabled).toBe(false);
  });

  it('never enables the mock in production, whatever the flag says', () => {
    expect(readConfig({ DEV: false, VITE_AI_MOCK: 'true' }).mockEnabled).toBe(false);
    expect(readConfig({ VITE_AI_MOCK: 'true' }).mockEnabled).toBe(false);
  });

  it('rejects cleartext http for non-local hosts in production but allows it for local development', () => {
    expect(() => readConfig({ DEV: false, VITE_API_BASE_URL: 'http://api.example.com' })).toThrow(/https/);
    expect(readConfig({ DEV: false, VITE_API_BASE_URL: 'http://localhost:8080' }).apiBaseUrl).toBe('http://localhost:8080');
    expect(readConfig({ DEV: true, VITE_API_BASE_URL: 'http://dev-box:8080' }).apiBaseUrl).toBe('http://dev-box:8080');
    expect(readConfig({ DEV: false, VITE_API_BASE_URL: 'https://api.example.com' }).apiBaseUrl).toBe('https://api.example.com');
  });
});
