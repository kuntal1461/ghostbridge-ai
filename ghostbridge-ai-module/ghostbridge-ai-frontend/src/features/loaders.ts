import { getProviderStatus, getUsage } from '../api/aiApi';

/** Stable module-level loaders for useApiResource. */
export const loadProviderStatus = (signal: AbortSignal) => getProviderStatus({ signal });
export const loadUsage = (signal: AbortSignal) => getUsage({ signal });
