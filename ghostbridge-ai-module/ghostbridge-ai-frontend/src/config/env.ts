/** Non-secret frontend configuration. Every VITE_* value is public (embedded in the bundle): never put a secret here. */

export interface RawEnv {
  readonly VITE_API_BASE_URL?: string;
  readonly VITE_API_TIMEOUT_MS?: string;
  readonly VITE_AI_STRUCTURED_SCHEMAS?: string;
  readonly VITE_AI_MOCK?: string;
  readonly DEV?: boolean;
}

export interface AppConfig {
  /** Empty string means "same origin". No trailing slash. */
  readonly apiBaseUrl: string;
  readonly timeoutMs: number;
  /** Operator-approved structured-output schema identifiers the UI may offer. */
  readonly structuredSchemas: readonly string[];
  /** True only in a development build with VITE_AI_MOCK=true. Always false in production. */
  readonly mockEnabled: boolean;
}

export const DEFAULT_TIMEOUT_MS = 60_000;
export const MIN_TIMEOUT_MS = 1_000;
export const MAX_TIMEOUT_MS = 300_000;
export const DEFAULT_SCHEMAS: readonly string[] = ['generic-summary'];
/** Same pattern as the backend's REGEX_SCHEMA_NAME. */
export const SCHEMA_NAME_PATTERN = /^[a-z0-9][a-z0-9-]{0,63}$/;

const LOCAL_HOSTS = new Set(['localhost', '127.0.0.1', '[::1]']);

function parseBaseUrl(value: string | undefined, dev: boolean): string {
  const trimmed = (value ?? '').trim();
  if (trimmed === '') return '';
  let url: URL;
  try {
    url = new URL(trimmed);
  } catch {
    throw new Error('VITE_API_BASE_URL must be an absolute http(s) URL or empty.');
  }
  if ((url.protocol !== 'https:' && url.protocol !== 'http:') || url.username !== '' || url.password !== '') {
    throw new Error('VITE_API_BASE_URL must be an http(s) URL without embedded credentials.');
  }
  if (url.protocol === 'http:' && !dev && !LOCAL_HOSTS.has(url.hostname)) {
    throw new Error('VITE_API_BASE_URL must use https outside local development.');
  }
  return trimmed.replace(/\/+$/, '');
}

function parseTimeout(value: string | undefined): number {
  const parsed = Number(value);
  if (value === undefined || value.trim() === '' || !Number.isInteger(parsed)) return DEFAULT_TIMEOUT_MS;
  return Math.min(MAX_TIMEOUT_MS, Math.max(MIN_TIMEOUT_MS, parsed));
}

function parseSchemas(value: string | undefined): readonly string[] {
  const names = (value ?? '')
    .split(',')
    .map((name) => name.trim())
    .filter((name) => SCHEMA_NAME_PATTERN.test(name));
  return names.length > 0 ? [...new Set(names)] : DEFAULT_SCHEMAS;
}

/** Pure and testable. `mockEnabled` requires BOTH the explicit flag and a development build. */
export function readConfig(raw: RawEnv): AppConfig {
  return {
    apiBaseUrl: parseBaseUrl(raw.VITE_API_BASE_URL, raw.DEV === true),
    timeoutMs: parseTimeout(raw.VITE_API_TIMEOUT_MS),
    structuredSchemas: parseSchemas(raw.VITE_AI_STRUCTURED_SCHEMAS),
    mockEnabled: raw.DEV === true && raw.VITE_AI_MOCK === 'true',
  };
}

export const config: AppConfig = readConfig(import.meta.env);

/** Compile-time gate: in production `import.meta.env.DEV` is the literal `false`, so mock code is removed. */
export const MOCK_ACTIVE: boolean = import.meta.env.DEV && config.mockEnabled;
