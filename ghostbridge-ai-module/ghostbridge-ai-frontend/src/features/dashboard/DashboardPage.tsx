import { Link } from 'react-router-dom';
import { PageHeader } from '../../components/feedback/PageHeader';
import { ErrorPanel } from '../../components/feedback/ErrorPanel';
import { ResourceView } from '../../components/feedback/ResourceView';
import { Skeleton } from '../../components/feedback/Skeleton';
import { useApiResource, type ResourceState } from '../../hooks/useApiResource';
import { loadProviderStatus, loadUsage } from '../loaders';
import { ProviderStatusCard } from '../provider/ProviderStatusCard';
import { RateLimitCard } from '../usage/RateLimitCard';
import { UsageSummary } from '../usage/UsageSummary';

/** Access problems apply to the whole API, so they replace the page content instead of repeating per card. */
function blockingError(...states: ResourceState<unknown>[]) {
  for (const state of states) {
    if (state.status === 'error' && (state.error.kind === 'unauthenticated' || state.error.kind === 'forbidden')) return state.error;
  }
  return undefined;
}

export function DashboardPage() {
  const provider = useApiResource(loadProviderStatus);
  const usage = useApiResource(loadUsage);
  const blocking = blockingError(provider.state, usage.state);
  const loading = provider.state.status === 'loading' || usage.state.status === 'loading';
  const refreshAll = () => {
    provider.refresh();
    usage.refresh();
  };

  return (
    <>
      <PageHeader
        title="Dashboard"
        description="Overview of the GhostBridge AI platform as reported by the backend."
        actions={<button type="button" className="button button--secondary" onClick={refreshAll} disabled={loading}>Refresh</button>}
      />
      {blocking ? (
        loading ? <Skeleton label="Loading dashboard" /> : <ErrorPanel error={blocking} />
      ) : (
        <div className="grid">
          <ResourceView state={provider.state} loadingLabel="Loading provider status" onRetry={provider.refresh}>
            {(status) => <ProviderStatusCard status={status} idPrefix="dash" />}
          </ResourceView>
          <ResourceView state={usage.state} loadingLabel="Loading usage" onRetry={usage.refresh}>
            {(data) => (
              <>
                <UsageSummary usage={data} idPrefix="dash" />
                <RateLimitCard rateLimit={data.rateLimit} idPrefix="dash" />
              </>
            )}
          </ResourceView>
          <section className="card" aria-labelledby="dash-start">
            <h2 id="dash-start" className="card__title">Try it</h2>
            <p className="muted">Send a prompt template to the model, or request schema-validated JSON.</p>
            <p className="link-row">
              <Link className="button button--primary" to="/playground">Open AI Playground</Link>
              <Link className="button button--secondary" to="/structured">Open Structured Output</Link>
            </p>
          </section>
        </div>
      )}
    </>
  );
}
