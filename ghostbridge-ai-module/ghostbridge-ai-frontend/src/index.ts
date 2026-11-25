/** Public surface for embedding the AI console in GhostBridge App. */
export { App as AiConsole } from './App';
export { requestJson } from './api/client';
export { ErrorPanel } from './components/feedback/ErrorPanel';
export { setUnauthenticatedHandler } from './api/auth';
export {
  bearerAuth,
  sessionCookieAuth,
  setAuthStrategy as setAiAuthStrategy,
  type AuthStrategy,
} from './api/auth';
export { generate, generateStructured, getProviderStatus, getUsage } from './api/aiApi';
export { ApiError, isApiError, type ApiErrorKind } from './api/errors';
export type * from './api/types';
