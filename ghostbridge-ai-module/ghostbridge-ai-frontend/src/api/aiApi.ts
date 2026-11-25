import { requestJson } from './client';
import { parseGenerateResponse, parseProviderStatus, parseStructuredResponse, parseUsage } from './guards';
import type {
  GenerateRequest,
  GenerateResponse,
  ProviderStatus,
  StructuredGenerateRequest,
  StructuredGenerateResponse,
  Usage,
} from './types';

/** Typed AI Web endpoints. The paths are the contract in GhostBridgeAiLiterals (API_BASE_PATH + PATH_*). */
const BASE = '/api/v1/ai';

export interface CallOptions {
  signal?: AbortSignal;
}

export async function generate(request: GenerateRequest, options: CallOptions = {}): Promise<GenerateResponse> {
  return parseGenerateResponse(await requestJson({ method: 'POST', path: `${BASE}/generate`, body: request, signal: options.signal }));
}

export async function generateStructured(
  request: StructuredGenerateRequest,
  options: CallOptions = {},
): Promise<StructuredGenerateResponse> {
  return parseStructuredResponse(
    await requestJson({ method: 'POST', path: `${BASE}/generate/structured`, body: request, signal: options.signal }),
  );
}

export async function getProviderStatus(options: CallOptions = {}): Promise<ProviderStatus> {
  return parseProviderStatus(await requestJson({ method: 'GET', path: `${BASE}/provider/status`, signal: options.signal }));
}

export async function getUsage(options: CallOptions = {}): Promise<Usage> {
  return parseUsage(await requestJson({ method: 'GET', path: `${BASE}/usage`, signal: options.signal }));
}
