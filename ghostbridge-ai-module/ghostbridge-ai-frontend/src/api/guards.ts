import { ApiError } from './errors';
import type {
  GenerateResponse,
  Json,
  ProviderStatus,
  RateLimit,
  StructuredGenerateResponse,
  TokenUsage,
  Usage,
} from './types';

/** Runtime validation of response bodies. A body that does not match the contract is an `invalid-response` error. */

function invalid(): never {
  throw new ApiError('invalid-response');
}

type Obj = Record<string, unknown>;

function obj(value: unknown): Obj {
  return typeof value === 'object' && value !== null && !Array.isArray(value) ? (value as Obj) : invalid();
}

function str(source: Obj, key: string): string {
  const value = source[key];
  return typeof value === 'string' ? value : invalid();
}

function optStr(source: Obj, key: string): string | undefined {
  const value = source[key];
  if (value === undefined || value === null) return undefined;
  return typeof value === 'string' ? value : invalid();
}

function num(source: Obj, key: string): number {
  const value = source[key];
  return typeof value === 'number' && Number.isFinite(value) ? value : invalid();
}

function optNum(source: Obj, key: string): number | undefined {
  const value = source[key];
  if (value === undefined || value === null) return undefined;
  return typeof value === 'number' && Number.isFinite(value) ? value : invalid();
}

function json(value: unknown): Json {
  return value as Json;
}

function usageOrUndefined(value: unknown): TokenUsage | undefined {
  if (value === undefined || value === null) return undefined;
  const source = obj(value);
  const usage: TokenUsage = {
    inputTokens: optNum(source, 'inputTokens'),
    outputTokens: optNum(source, 'outputTokens'),
    totalTokens: optNum(source, 'totalTokens'),
    estimatedCost: optNum(source, 'estimatedCost'),
  };
  return usage;
}

export function parseGenerateResponse(value: unknown): GenerateResponse {
  const source = obj(value);
  return {
    requestId: str(source, 'requestId'),
    model: str(source, 'model'),
    text: str(source, 'text'),
    structured: source.structured === undefined || source.structured === null ? undefined : json(source.structured),
    usage: usageOrUndefined(source.usage),
    durationMs: num(source, 'durationMs'),
  };
}

export function parseStructuredResponse(value: unknown): StructuredGenerateResponse {
  const source = obj(value);
  if (source.structured === undefined || source.structured === null) invalid();
  return {
    requestId: str(source, 'requestId'),
    schemaName: str(source, 'schemaName'),
    model: str(source, 'model'),
    structured: json(source.structured),
    usage: usageOrUndefined(source.usage),
    durationMs: num(source, 'durationMs'),
  };
}

export function parseProviderStatus(value: unknown): ProviderStatus {
  const source = obj(value);
  const capabilities = source.capabilities ?? [];
  if (typeof source.configured !== 'boolean' || !Array.isArray(capabilities) || capabilities.some((c) => typeof c !== 'string')) {
    invalid();
  }
  return {
    configured: source.configured,
    status: str(source, 'status'),
    provider: optStr(source, 'provider'),
    model: optStr(source, 'model'),
    capabilities: capabilities as string[],
  };
}

function rateLimitOrUndefined(value: unknown): RateLimit | undefined {
  if (value === undefined || value === null) return undefined;
  const source = obj(value);
  return {
    limitPerMinute: num(source, 'limitPerMinute'),
    remaining: num(source, 'remaining'),
    resetInSeconds: num(source, 'resetInSeconds'),
    maxConcurrent: num(source, 'maxConcurrent'),
  };
}

export function parseUsage(value: unknown): Usage {
  const source = obj(value);
  return {
    scope: str(source, 'scope'),
    historyLimit: num(source, 'historyLimit'),
    requestCount: num(source, 'requestCount'),
    successCount: num(source, 'successCount'),
    failureCount: num(source, 'failureCount'),
    requestsWithTokenUsage: num(source, 'requestsWithTokenUsage'),
    inputTokens: num(source, 'inputTokens'),
    outputTokens: num(source, 'outputTokens'),
    totalTokens: num(source, 'totalTokens'),
    rateLimit: rateLimitOrUndefined(source.rateLimit),
  };
}
