import type { ReactNode } from 'react';
import type { MutationState } from '../../hooks/useApiMutation';
import { ErrorPanel } from './ErrorPanel';
import { Skeleton } from './Skeleton';

interface MutationResultProps<T> {
  state: MutationState<T>;
  idleText: string;
  children: (data: T) => ReactNode;
}

/** Result pane shared by the write pages: idle hint, loading skeleton, error panel or the success view. */
export function MutationResult<T>({ state, idleText, children }: MutationResultProps<T>) {
  return (
    <section className="panel" aria-label="Result" aria-live="polite">
      {state.status === 'idle' ? <p className="muted">{idleText}</p> : null}
      {state.status === 'pending' ? <Skeleton label="Waiting for the model" lines={4} /> : null}
      {state.status === 'error' ? <ErrorPanel error={state.error} /> : null}
      {state.status === 'success' ? children(state.data) : null}
    </section>
  );
}
