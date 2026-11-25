import { act, renderHook } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { ApiError } from '../api/errors';
import { useApiMutation } from './useApiMutation';

describe('useApiMutation', () => {
  it('aborts the request on unmount', () => {
    let seen: AbortSignal | undefined;
    const { result, unmount } = renderHook(() =>
      useApiMutation((_request: string, signal: AbortSignal) => {
        seen = signal;
        return new Promise<string>(() => undefined);
      }),
    );
    void act(() => {
      void result.current.run('x');
    });
    unmount();
    expect(seen?.aborted).toBe(true);
  });

  it('treats an aborted ApiError as idle, not as an error', async () => {
    const { result } = renderHook(() => useApiMutation(() => Promise.reject(new ApiError('aborted'))));
    await act(() => result.current.run('x'));
    expect(result.current.state.status).toBe('idle');
  });

  it('does not run twice while pending', async () => {
    let calls = 0;
    const { result } = renderHook(() =>
      useApiMutation(() => {
        calls += 1;
        return new Promise<string>(() => undefined);
      }),
    );
    void act(() => {
      void result.current.run('a');
      void result.current.run('b');
    });
    expect(calls).toBe(1);
  });
});
