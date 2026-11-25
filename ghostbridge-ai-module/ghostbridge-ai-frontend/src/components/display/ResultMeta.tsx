import type { TokenUsage } from '../../api/types';
import { formatCount, formatDuration } from '../../lib/format';

interface ResultMetaProps {
  requestId: string;
  model: string;
  durationMs: number;
  usage?: TokenUsage;
}

/** Shows only what the response contains. Missing token fields are omitted, never estimated. */
export function ResultMeta({ requestId, model, durationMs, usage }: ResultMetaProps) {
  const tokens: [string, number | undefined][] = [
    ['Input tokens', usage?.inputTokens],
    ['Output tokens', usage?.outputTokens],
    ['Total tokens', usage?.totalTokens],
    ['Estimated cost', usage?.estimatedCost],
  ];
  return (
    <dl className="meta">
      <div><dt>Model</dt><dd>{model}</dd></div>
      <div><dt>Duration</dt><dd>{formatDuration(durationMs)}</dd></div>
      <div><dt>Request ID</dt><dd>{requestId}</dd></div>
      {tokens
        .filter((entry): entry is [string, number] => entry[1] !== undefined)
        .map(([label, value]) => (
          <div key={label}><dt>{label}</dt><dd>{formatCount(value)}</dd></div>
        ))}
      {usage === undefined ? <div><dt>Token usage</dt><dd>not reported</dd></div> : null}
    </dl>
  );
}
