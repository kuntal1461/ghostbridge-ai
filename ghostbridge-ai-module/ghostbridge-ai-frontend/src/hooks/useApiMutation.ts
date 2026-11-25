import { useCallback, useEffect, useRef, useState } from 'react';
import { toApiError, type ApiError } from '../api/errors';

export type MutationState<T> =
  | { status: 'idle' }
  | { status: 'pending' }
  | { status: 'success'; data: T }
  | { status: 'error'; error: ApiError };

/**
 * Runs one POST at a time. A second `run` while one is in flight is ignored (no duplicate submissions), failures are
 * never retried automatically, and the request is cancelled on unmount, `cancel` and `reset`.
 * A cancelled request returns to `idle`; it is not an error.
 */
export function useApiMutation<TRequest, TResponse>(
  action: (request: TRequest, signal: AbortSignal) => Promise<TResponse>,
) {
  const [state, setState] = useState<MutationState<TResponse>>({ status: 'idle' });
  const controllerRef = useRef<AbortController | null>(null);

  useEffect(() => () => controllerRef.current?.abort(), []);

  const run = useCallback(
    async (request: TRequest) => {
      if (controllerRef.current) return;
      const controller = new AbortController();
      controllerRef.current = controller;
      setState({ status: 'pending' });
      try {
        const data = await action(request, controller.signal);
        if (!controller.signal.aborted) setState({ status: 'success', data });
      } catch (error) {
        if (controller.signal.aborted) return;
        const apiError = toApiError(error);
        setState(apiError.kind === 'aborted' ? { status: 'idle' } : { status: 'error', error: apiError });
      } finally {
        if (controllerRef.current === controller) controllerRef.current = null;
      }
    },
    [action],
  );

  const cancel = useCallback(() => {
    controllerRef.current?.abort();
    controllerRef.current = null;
    setState((current) => (current.status === 'pending' ? { status: 'idle' } : current));
  }, []);

  const reset = useCallback(() => {
    controllerRef.current?.abort();
    controllerRef.current = null;
    setState({ status: 'idle' });
  }, []);

  return { state, run, cancel, reset };
}
