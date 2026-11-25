import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthProvider';

/** Renders its children only for a signed-in user; otherwise sends the visitor to the sign-in page and back afterwards. */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { status, retry } = useAuth();
  const location = useLocation();

  if (status === 'loading') {
    return (
      <p className="auth-status" role="status">
        Checking your session…
      </p>
    );
  }
  if (status === 'unavailable') {
    return (
      <div className="auth-status error-panel" role="alert">
        <h2 className="error-panel__title">Backend unreachable</h2>
        <p>GhostBridge could not check your session. Check that the server is running.</p>
        <button type="button" className="button button--secondary" onClick={retry}>
          Try again
        </button>
      </div>
    );
  }
  if (status === 'anonymous') return <Navigate to="/login" replace state={{ from: `${location.pathname}${location.search}` }} />;
  return <>{children}</>;
}
