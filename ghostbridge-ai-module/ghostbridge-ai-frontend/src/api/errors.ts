export type ApiErrorKind =
  | 'validation' // 400
  | 'unauthenticated' // 401
  | 'forbidden' // 403
  | 'payload-too-large' // 413
  | 'rate-limited' // 429
  | 'bad-gateway' // 502
  | 'unavailable' // 503
  | 'gateway-timeout' // 504
  | 'http' // any other non-2xx status
  | 'network' // fetch rejected
  | 'timeout' // client-side timeout
  | 'aborted' // caller cancelled
  | 'invalid-response'; // 2xx with a body that does not match the contract

export interface FieldError {
  field: string;
  message: string;
}

/** Fixed, client-safe wording. Server text is never used as the headline. */
const MESSAGES: Record<ApiErrorKind, string> = {
  validation: 'The request was rejected as invalid.',
  unauthenticated: 'Authentication is required to use the AI API.',
  forbidden: 'Access denied. Your account is not permitted to use the AI API.',
  'payload-too-large': 'The request is too large. Shorten the input and try again.',
  'rate-limited': 'Rate limit reached. Please wait before sending another request.',
  'bad-gateway': 'The AI service could not reach its provider (bad gateway).',
  unavailable: 'The AI service is currently unavailable.',
  'gateway-timeout': 'The AI service timed out waiting for its provider.',
  http: 'The server returned an unexpected error.',
  network: 'The backend could not be reached. Check your connection and the API address.',
  timeout: 'The request took too long and was stopped.',
  aborted: 'The request was cancelled.',
  'invalid-response': 'The server returned a response the UI could not understand.',
};

export interface ApiErrorInit {
  status?: number;
  code?: string;
  reason?: string;
  correlationId?: string;
  detail?: string;
  retryAfterSeconds?: number;
  fieldErrors?: FieldError[];
}

export class ApiError extends Error {
  readonly kind: ApiErrorKind;
  readonly status?: number;
  readonly code?: string;
  readonly reason?: string;
  readonly correlationId?: string;
  /** Server-supplied explanatory text (4xx only); render as escaped text. */
  readonly detail?: string;
  /** Present only when the server sent a valid integer Retry-After. */
  readonly retryAfterSeconds?: number;
  readonly fieldErrors: FieldError[];

  constructor(kind: ApiErrorKind, init: ApiErrorInit = {}) {
    super(MESSAGES[kind]);
    this.name = 'ApiError';
    this.kind = kind;
    this.status = init.status;
    this.code = init.code;
    this.reason = init.reason;
    this.correlationId = init.correlationId;
    this.detail = init.detail;
    this.retryAfterSeconds = init.retryAfterSeconds;
    this.fieldErrors = init.fieldErrors ?? [];
  }
}

const KIND_BY_STATUS: Record<number, ApiErrorKind> = {
  400: 'validation',
  401: 'unauthenticated',
  403: 'forbidden',
  413: 'payload-too-large',
  429: 'rate-limited',
  502: 'bad-gateway',
  503: 'unavailable',
  504: 'gateway-timeout',
};

export function kindForStatus(status: number): ApiErrorKind {
  return KIND_BY_STATUS[status] ?? 'http';
}

export function isApiError(value: unknown): value is ApiError {
  return value instanceof ApiError;
}

/** Anything thrown that is not an ApiError is reported generically; its text is never shown. */
export function toApiError(error: unknown): ApiError {
  return isApiError(error) ? error : new ApiError('network');
}
