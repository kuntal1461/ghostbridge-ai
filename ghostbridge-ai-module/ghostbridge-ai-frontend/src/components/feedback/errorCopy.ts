import type { ApiError, ApiErrorKind } from '../../api/errors';

interface ErrorCopy {
  title: string;
  guidance: string;
}

const COPY: Record<ApiErrorKind, ErrorCopy> = {
  unauthenticated: {
    title: 'Authentication required',
    guidance:
      'The AI API only answers signed-in users. This console has no login of its own; it uses the authentication provided by the GhostBridge application.',
  },
  forbidden: {
    title: 'Access denied',
    guidance: 'You are signed in, but your account does not have the role required to use the AI API. Ask an administrator for access.',
  },
  'rate-limited': { title: 'Rate limit reached', guidance: 'Too many requests were sent. Wait before trying again.' },
  validation: { title: 'Request rejected', guidance: 'Check the highlighted values and submit again.' },
  'payload-too-large': { title: 'Request too large', guidance: 'Shorten the variables or untrusted content and submit again.' },
  'bad-gateway': { title: 'Provider error', guidance: 'The AI service could not get a valid answer from its provider. Try again later.' },
  unavailable: { title: 'Service unavailable', guidance: 'The AI service or its provider is not available right now. Try again later.' },
  'gateway-timeout': { title: 'Provider timed out', guidance: 'The provider did not answer in time. Try again later or use a shorter input.' },
  network: { title: 'Backend unreachable', guidance: 'The browser could not reach the GhostBridge backend. Check that it is running and the API address is correct.' },
  timeout: { title: 'Request timed out', guidance: 'The request was stopped after the configured timeout.' },
  aborted: { title: 'Request cancelled', guidance: 'The request was cancelled.' },
  'invalid-response': { title: 'Unexpected response', guidance: 'The server answered, but not in the expected format. The backend and this UI may be out of sync.' },
  http: { title: 'Server error', guidance: 'The server could not complete the request.' },
};

export function errorCopy(error: ApiError): ErrorCopy {
  return COPY[error.kind];
}

/** Retry guidance is built only from a Retry-After value the server really sent. */
export function retryGuidance(error: ApiError): string | undefined {
  if (error.kind !== 'rate-limited') return undefined;
  return error.retryAfterSeconds === undefined
    ? 'The server did not say how long to wait. Try again in a little while.'
    : `The server asks you to wait ${error.retryAfterSeconds} ${error.retryAfterSeconds === 1 ? 'second' : 'seconds'} before retrying.`;
}
