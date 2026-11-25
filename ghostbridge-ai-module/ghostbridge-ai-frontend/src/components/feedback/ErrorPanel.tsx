import type { ApiError } from '../../api/errors';
import { errorCopy, retryGuidance } from './errorCopy';

interface ErrorPanelProps {
  error: ApiError;
  /** Offered only for read requests, and only as an explicit user action. */
  onRetry?: () => void;
}

/** All text is rendered through React's escaping. Server-supplied text is limited to `detail` and field messages. */
export function ErrorPanel({ error, onRetry }: ErrorPanelProps) {
  const { title, guidance } = errorCopy(error);
  const retry = retryGuidance(error);
  return (
    <div className={`error-panel error-panel--${error.kind}`} role="alert">
      <h2 className="error-panel__title">{title}</h2>
      <p>{guidance}</p>
      {retry ? <p className="error-panel__retry">{retry}</p> : null}
      {error.detail ? <p className="muted">Server message: {error.detail}</p> : null}
      {error.fieldErrors.length > 0 ? (
        <ul className="error-panel__fields">
          {error.fieldErrors.map((item, index) => (
            <li key={`${item.field}-${index}`}>
              <strong>{item.field}</strong>: {item.message}
            </li>
          ))}
        </ul>
      ) : null}
      {error.correlationId ? <p className="muted">Reference: {error.correlationId}</p> : null}
      {onRetry ? (
        <button type="button" className="button button--secondary" onClick={onRetry}>
          Try again
        </button>
      ) : null}
    </div>
  );
}
