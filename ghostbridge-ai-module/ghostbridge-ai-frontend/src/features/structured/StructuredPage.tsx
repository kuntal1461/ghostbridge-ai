import { useState, type FormEvent } from 'react';
import { generateStructured } from '../../api/aiApi';
import type { StructuredGenerateRequest } from '../../api/types';
import { JsonViewer } from '../../components/display/JsonViewer';
import { ResultMeta } from '../../components/display/ResultMeta';
import { RequestFields } from '../../components/form/RequestFields';
import { SelectField } from '../../components/form/SelectField';
import { FormActions } from '../../components/form/FormActions';
import { MutationResult } from '../../components/feedback/MutationResult';
import { PageHeader } from '../../components/feedback/PageHeader';
import { config } from '../../config/env';
import { useApiMutation } from '../../hooks/useApiMutation';
import { initialRequestFields, validateRequestFields, type FieldErrors, type RequestFieldsState } from '../requestFields';

const run = (request: StructuredGenerateRequest, signal: AbortSignal) => generateStructured(request, { signal });

export function StructuredPage() {
  const [schemaName, setSchemaName] = useState(config.structuredSchemas[0]);
  const [fields, setFields] = useState<RequestFieldsState>(initialRequestFields);
  const [errors, setErrors] = useState<FieldErrors>({});
  const mutation = useApiMutation(run);
  const pending = mutation.state.status === 'pending';

  const submit = (event: FormEvent) => {
    event.preventDefault();
    const { errors: found, parsed } = validateRequestFields(fields);
    setErrors(found);
    if (Object.keys(found).length > 0) return;
    void mutation.run({
      schemaName,
      variables: Object.keys(parsed.variables).length > 0 ? parsed.variables : undefined,
      untrustedContent: parsed.untrustedContent,
      options: parsed.options,
    });
  };

  const reset = () => {
    mutation.reset();
    setErrors({});
    setSchemaName(config.structuredSchemas[0]);
    setFields(initialRequestFields());
  };

  return (
    <>
      <PageHeader
        title="Structured Output"
        description="Request JSON that the backend validates against an operator-approved schema. Schemas cannot be created here."
      />
      <div className="split">
        <form className="panel" onSubmit={submit} noValidate aria-busy={pending}>
          <SelectField label="Approved schema" value={schemaName} options={config.structuredSchemas} disabled={pending} hint="Only schema identifiers approved by the operator are accepted." onChange={setSchemaName} />
          <RequestFields value={fields} errors={errors} disabled={pending} onChange={setFields} />
          {Object.keys(errors).length > 0 ? <p className="field__error" role="alert">Fix the highlighted fields and submit again.</p> : null}
          <FormActions pending={pending} submitLabel="Generate JSON" onCancel={mutation.cancel} onReset={reset} />
        </form>
        <MutationResult state={mutation.state} idleText="The validated JSON will appear here.">
          {(response) => (
            <div className="result">
              <ResultMeta requestId={response.requestId} model={response.model} durationMs={response.durationMs} usage={response.usage} />
              <p className="muted">Schema: {response.schemaName}</p>
              <JsonViewer value={response.structured} label="Structured JSON" />
            </div>
          )}
        </MutationResult>
      </div>
    </>
  );
}
