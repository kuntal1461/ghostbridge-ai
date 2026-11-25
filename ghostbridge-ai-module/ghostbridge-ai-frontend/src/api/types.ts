/** Types mirror the Java RequestVO/ResponseVO records of ghostbridge-ai-web. Optional means "omitted when null" (NON_NULL). */

export type Json = string | number | boolean | null | Json[] | { [key: string]: Json };

export interface GenerationOptions {
  /** 1..32768 */
  maxOutputTokens?: number;
  /** 0.0..1.0 */
  temperature?: number;
}

/** AiGenerateRequestVO */
export interface GenerateRequest {
  promptName: string;
  promptVersion?: number;
  variables?: Record<string, string>;
  untrustedContent?: string;
  modelId?: string;
  options?: GenerationOptions;
}

/** AiStructuredGenerateRequestVO (no promptName, no modelId) */
export interface StructuredGenerateRequest {
  schemaName: string;
  variables?: Record<string, string>;
  untrustedContent?: string;
  options?: GenerationOptions;
}

/** AiTokenUsageResponseVO: only what the provider reported. */
export interface TokenUsage {
  inputTokens?: number;
  outputTokens?: number;
  totalTokens?: number;
  estimatedCost?: number;
}

/** AiGenerateResponseVO. `text` is untrusted model output. */
export interface GenerateResponse {
  requestId: string;
  model: string;
  text: string;
  structured?: Json;
  usage?: TokenUsage;
  durationMs: number;
}

/** AiStructuredGenerateResponseVO */
export interface StructuredGenerateResponse {
  requestId: string;
  schemaName: string;
  model: string;
  structured: Json;
  usage?: TokenUsage;
  durationMs: number;
}

/** AiProviderStatusResponseVO. `configured` is configuration status, not a live availability probe. */
export interface ProviderStatus {
  configured: boolean;
  status: string;
  provider?: string;
  model?: string;
  capabilities: string[];
}

/** AiRateLimitResponseVO */
export interface RateLimit {
  limitPerMinute: number;
  remaining: number;
  resetInSeconds: number;
  maxConcurrent: number;
}

/** AiUsageResponseVO: the caller's slice of a bounded retained history, not lifetime usage. */
export interface Usage {
  scope: string;
  historyLimit: number;
  requestCount: number;
  successCount: number;
  failureCount: number;
  requestsWithTokenUsage: number;
  inputTokens: number;
  outputTokens: number;
  totalTokens: number;
  rateLimit?: RateLimit;
}
