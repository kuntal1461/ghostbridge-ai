import { useCallback, useEffect, useReducer, useState } from 'react';
import { toApiError, type ApiError } from '../api/errors';

export type ResourceState<T> =
  | { status: 'loading' }
  | { status: 'success'; data: T }
  | { status: 'error'; error: ApiError };

type Action<T> = { type: 'start' } | { type: 'success'; data: T } | { type: 'error'; error: ApiError };

function reducer<T>(_state: ResourceState<T>, action: Action<T>): ResourceState<T> {
  switch (action.type) {
    case 'start':
      return { status: 'loading' };
    case 'success':
      return { status: 'success', data: action.data };
    case 'error':
      return { status: 'error', error: action.error };
  }
}

/**
 * Loads a GET resource once on mount and again only when `refresh` is called (a user action).
 * There is no polling and no automatic retry. In-flight requests are cancelled on unmount and on refresh.
 * `load` must be a stable function (module-level or memoised).
 */
export function useApiResource<T>(load: (signal: AbortSignal) => Promise<T>) {
  const [state, dispatch] = useReducer(reducer<T>, { status: 'loading' });
  const [round, setRound] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    load(controller.signal).then(
      (data) => {
        if (!controller.signal.aborted) dispatch({ type: 'success', data });
      },
      (error: unknown) => {
        if (controller.signal.aborted) return;
        dispatch({ type: 'error', error: toApiError(error) });
      },
    );
    return () => controller.abort();
  }, [load, round]);

  const refresh = useCallback(() => {
    dispatch({ type: 'start' });
    setRound((value) => value + 1);
  }, []);

  return { state, refresh };
}
