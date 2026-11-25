import { useState, type FormEvent } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from 'ghostbridge-ai-frontend';
import { useAuth } from '../auth/AuthProvider';

function messageFor(error: unknown): string {
  if (!(error instanceof ApiError)) return 'Sign-in failed. Please try again.';
  if (error.kind === 'unauthenticated') return 'Sign-in failed. Check your user name and password.';
  if (error.kind === 'rate-limited') {
    return error.retryAfterSeconds === undefined
      ? 'Too many failed attempts. Please wait before trying again.'
      : `Too many failed attempts. Try again in ${error.retryAfterSeconds} ${error.retryAfterSeconds === 1 ? 'second' : 'seconds'}.`;
  }
  return error.message;
}

/** Only an in-app path is a valid post-login target: never a protocol-relative or absolute URL. */
function safeDestination(from: string | undefined): string {
  return from !== undefined && /^\/(?![/\\])/.test(from) ? from : '/';
}

export function LoginPage() {
  const { status, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const destination = safeDestination((location.state as { from?: string } | null)?.from);
  if (status === 'authenticated') return <Navigate to={destination} replace />;

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (pending) return;
    setPending(true);
    setError(null);
    try {
      await login(username.trim(), password);
      navigate(destination, { replace: true });
    } catch (failure) {
      setError(messageFor(failure));
      setPassword('');
    } finally {
      setPending(false);
    }
  };

  return (
    <main className="login">
      <form className="panel login__form" onSubmit={(event) => void submit(event)} aria-busy={pending}>
        <h1>GhostBridge</h1>
        <p className="muted">Sign in to use the AI console.</p>
        <div className="field">
          <label htmlFor="username">User name</label>
          <input id="username" className="input" autoComplete="username" required value={username} readOnly={pending}
            aria-invalid={error ? true : undefined} aria-describedby={error ? 'login-error' : undefined}
            onChange={(event) => setUsername(event.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="password">Password</label>
          <input id="password" className="input" type="password" autoComplete="current-password" required value={password} readOnly={pending}
            aria-invalid={error ? true : undefined} aria-describedby={error ? 'login-error' : undefined}
            onChange={(event) => setPassword(event.target.value)} />
        </div>
        {error ? <p id="login-error" className="field__error" role="alert">{error}</p> : null}
        <button type="submit" className="button button--primary" disabled={pending || username.trim() === '' || password === ''}>
          {pending ? 'Signing in…' : 'Sign in'}
        </button>
      </form>
    </main>
  );
}
