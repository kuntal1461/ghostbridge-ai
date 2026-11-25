import { PageHeader } from '../../components/feedback/PageHeader';
import { ResourceView } from '../../components/feedback/ResourceView';
import { useApiResource } from '../../hooks/useApiResource';
import { loadProviderStatus } from '../loaders';
import { ProviderStatusCard } from './ProviderStatusCard';

export function ProviderStatusPage() {
  const { state, refresh } = useApiResource(loadProviderStatus);
  return (
    <>
      <PageHeader
        title="Provider Status"
        description="Safe provider metadata exposed by the GhostBridge AI API. Credentials and connection details are never shown."
        actions={<button type="button" className="button button--secondary" onClick={refresh} disabled={state.status === 'loading'}>Refresh</button>}
      />
      <ResourceView state={state} loadingLabel="Loading provider status" onRetry={refresh}>
        {(status) => <ProviderStatusCard status={status} idPrefix="provider-page" />}
      </ResourceView>
    </>
  );
}
