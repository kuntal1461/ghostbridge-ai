import type { ProviderStatus } from '../../api/types';
import { Card } from '../../components/display/Card';
import { StatCard } from '../../components/display/StatCard';
import { StatusBadge } from '../../components/display/StatusBadge';
import { EmptyState } from '../../components/feedback/EmptyState';

interface ProviderStatusCardProps {
  status: ProviderStatus;
  idPrefix: string;
}

/** Everything shown here comes from AiProviderStatusResponseVO; nothing is inferred. */
export function ProviderStatusCard({ status, idPrefix }: ProviderStatusCardProps) {
  const ready = status.configured && status.status === 'AVAILABLE';
  return (
    <Card
      title="Provider"
      titleId={`${idPrefix}-provider`}
      note="This reports configuration status only. It does not prove the provider is reachable or that a request will succeed."
    >
      <dl className="stats">
        <StatCard label="Configured" value={<StatusBadge tone={status.configured ? 'good' : 'warn'}>{status.configured ? 'Yes' : 'No'}</StatusBadge>} />
        <StatCard label="Status" value={<StatusBadge tone={ready ? 'good' : 'warn'}>{status.status}</StatusBadge>} />
        <StatCard label="Provider" value={status.provider ?? 'Not reported'} />
        <StatCard label="Model" value={status.model ?? 'Not reported'} />
      </dl>
      <h3 className="card__subtitle">Capabilities</h3>
      {status.capabilities.length === 0 ? (
        <EmptyState title="No capabilities reported">The backend did not list any capabilities.</EmptyState>
      ) : (
        <ul className="chips">
          {status.capabilities.map((capability) => (
            <li key={capability} className="chip">{capability}</li>
          ))}
        </ul>
      )}
    </Card>
  );
}
