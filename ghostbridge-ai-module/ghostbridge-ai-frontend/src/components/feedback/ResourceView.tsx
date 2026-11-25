import type { ReactNode } from 'react';
import type { ResourceState } from '../../hooks/useApiResource';
import { ErrorPanel } from './ErrorPanel';
import { Skeleton } from './Skeleton';

interface ResourceViewProps<T> {
  state: ResourceState<T>;
  loadingLabel: string;
  onRetry: () => void;
  children: (data: T) => ReactNode;
}

/** One place for loading, error and success rendering of read requests. */
export function ResourceView<T>({ state, loadingLabel, onRetry, children }: ResourceViewProps<T>) {
  if (state.status === 'loading') return <Skeleton label={loadingLabel} />;
  if (state.status === 'error') return <ErrorPanel error={state.error} onRetry={onRetry} />;
  return <>{children(state.data)}</>;
}
