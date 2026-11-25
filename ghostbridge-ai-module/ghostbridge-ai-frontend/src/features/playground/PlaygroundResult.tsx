import type { GenerateResponse } from '../../api/types';
import { JsonViewer } from '../../components/display/JsonViewer';
import { ResultMeta } from '../../components/display/ResultMeta';
import { TextOutput } from '../../components/display/TextOutput';
import { EmptyState } from '../../components/feedback/EmptyState';

export function PlaygroundResult({ response }: { response: GenerateResponse }) {
  return (
    <div className="result">
      <ResultMeta requestId={response.requestId} model={response.model} durationMs={response.durationMs} usage={response.usage} />
      {response.text === '' ? <EmptyState title="The model returned no text" /> : <TextOutput text={response.text} label="Response (plain text)" />}
      {response.structured === undefined ? null : <JsonViewer value={response.structured} label="Structured output" />}
    </div>
  );
}
