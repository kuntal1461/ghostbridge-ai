import type { RateLimit } from '../../api/types';
import { Card } from '../../components/display/Card';
import { StatCard } from '../../components/display/StatCard';
import { EmptyState } from '../../components/feedback/EmptyState';

interface RateLimitCardProps {
  rateLimit?: RateLimit;
  idPrefix: string;
}

export function RateLimitCard({ rateLimit, idPrefix }: RateLimitCardProps) {
  return (
    <Card
      title="Your rate limit"
      titleId={`${idPrefix}-limit`}
      note="Per-requester request limit over a one-minute window on this server instance. Token budgets are global and not shown."
    >
      {rateLimit === undefined ? (
        <EmptyState title="No rate-limit information">The backend did not include rate-limit data.</EmptyState>
      ) : (
        <dl className="stats">
          <StatCard label="Limit per minute" value={rateLimit.limitPerMinute} />
          <StatCard label="Remaining" value={rateLimit.remaining} />
          <StatCard label="Window resets in" value={`${rateLimit.resetInSeconds} s`} />
          <StatCard label="Max concurrent" value={rateLimit.maxConcurrent} />
        </dl>
      )}
    </Card>
  );
}
