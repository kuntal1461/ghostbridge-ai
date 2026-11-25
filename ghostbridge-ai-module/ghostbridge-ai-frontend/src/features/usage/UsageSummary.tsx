import type { Usage } from '../../api/types';
import { Card } from '../../components/display/Card';
import { StatCard } from '../../components/display/StatCard';
import { EmptyState } from '../../components/feedback/EmptyState';
import { formatCount } from '../../lib/format';

const RETAINED_HISTORY = 'RETAINED_HISTORY';

export function scopeDescription(usage: Usage): string {
  return usage.scope === RETAINED_HISTORY
    ? `Your requests among the most recent ${formatCount(usage.historyLimit)} retained requests of all callers. Older activity is not counted, so this is not lifetime usage.`
    : `Scope: ${usage.scope} (history limit ${formatCount(usage.historyLimit)}).`;
}

export function UsageSummary({ usage, idPrefix }: { usage: Usage; idPrefix: string }) {
  return (
    <Card title="Usage (retained history)" titleId={`${idPrefix}-usage`} note={scopeDescription(usage)}>
      {usage.requestCount === 0 ? (
        <EmptyState title="No requests in the retained history">Send a request from the AI Playground and it will appear here.</EmptyState>
      ) : null}
      <dl className="stats">
        <StatCard label="Requests" value={formatCount(usage.requestCount)} />
        <StatCard label="Succeeded" value={formatCount(usage.successCount)} />
        <StatCard label="Failed" value={formatCount(usage.failureCount)} />
        <StatCard label="Input tokens" value={formatCount(usage.inputTokens)} />
        <StatCard label="Output tokens" value={formatCount(usage.outputTokens)} />
        <StatCard label="Total tokens" value={formatCount(usage.totalTokens)} hint={`Counted for ${formatCount(usage.requestsWithTokenUsage)} request(s) where the provider reported usage`} />
      </dl>
    </Card>
  );
}
