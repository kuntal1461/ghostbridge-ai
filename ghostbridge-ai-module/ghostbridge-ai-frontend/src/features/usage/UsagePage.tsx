import { PageHeader } from '../../components/feedback/PageHeader';
import { ResourceView } from '../../components/feedback/ResourceView';
import { useApiResource } from '../../hooks/useApiResource';
import { loadUsage } from '../loaders';
import { RateLimitCard } from './RateLimitCard';
import { UsageSummary } from './UsageSummary';

export function UsagePage() {
  const { state, refresh } = useApiResource(loadUsage);
  return (
    <>
      <PageHeader
        title="Usage & Limits"
        description="Your AI usage from the server's bounded request history, and your current rate limit."
        actions={<button type="button" className="button button--secondary" onClick={refresh} disabled={state.status === 'loading'}>Refresh</button>}
      />
      <ResourceView state={state} loadingLabel="Loading usage" onRetry={refresh}>
        {(usage) => (
          <div className="grid">
            <UsageSummary usage={usage} idPrefix="usage-page" />
            <RateLimitCard rateLimit={usage.rateLimit} idPrefix="usage-page" />
          </div>
        )}
      </ResourceView>
    </>
  );
}
