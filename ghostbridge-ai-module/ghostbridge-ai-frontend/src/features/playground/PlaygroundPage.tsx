import { useState, type FormEvent } from 'react';
import { generate } from '../../api/aiApi';
import type { GenerateRequest } from '../../api/types';
import { RequestFields } from '../../components/form/RequestFields';
import { TextField } from '../../components/form/TextField';
import { FormActions } from '../../components/form/FormActions';
import { MutationResult } from '../../components/feedback/MutationResult';
import { PageHeader } from '../../components/feedback/PageHeader';
import { useApiMutation } from '../../hooks/useApiMutation';
import { initialRequestFields, type FieldErrors } from '../requestFields';
import { PlaygroundResult } from './PlaygroundResult';
import { validatePlayground, type PlaygroundForm } from './validation';

const run = (request: GenerateRequest, signal: AbortSignal) => generate(request, { signal });

function initialForm(): PlaygroundForm {
  return { promptName: 'generic-structured', promptVersion: '', modelId: '', fields: initialRequestFields() };
}

export function PlaygroundPage() {
  const [form, setForm] = useState<PlaygroundForm>(initialForm);
  const [errors, setErrors] = useState<FieldErrors>({});
  const mutation = useApiMutation(run);
  const pending = mutation.state.status === 'pending';

  const submit = (event: FormEvent) => {
    event.preventDefault();
    const result = validatePlayground(form);
    setErrors(result.errors);
    if (result.request) void mutation.run(result.request);
  };

  const reset = () => {
    mutation.reset();
    setErrors({});
    setForm(initialForm());
  };

  return (
    <>
      <PageHeader
        title="AI Playground"
        description="Run a registered prompt template through the GhostBridge AI API. Free-form prompts are not accepted by the backend: provide the template name and its variables."
      />
      <div className="split">
        <form className="panel" onSubmit={submit} noValidate aria-busy={pending}>
          <TextField label="Prompt template name" required value={form.promptName} error={errors.promptName} disabled={pending} hint="A registered template, for example generic-structured." onChange={(promptName) => setForm({ ...form, promptName })} />
          <div className="field-row">
            <TextField label="Template version (optional)" inputMode="numeric" value={form.promptVersion} error={errors.promptVersion} disabled={pending} onChange={(promptVersion) => setForm({ ...form, promptVersion })} />
            <TextField label="Model id (optional)" value={form.modelId} error={errors.modelId} disabled={pending} hint="Must be an approved model; leave empty for the default." onChange={(modelId) => setForm({ ...form, modelId })} />
          </div>
          <RequestFields value={form.fields} errors={errors} disabled={pending} onChange={(fields) => setForm({ ...form, fields })} />
          {Object.keys(errors).length > 0 ? <p className="field__error" role="alert">Fix the highlighted fields and submit again.</p> : null}
          <FormActions pending={pending} submitLabel="Generate" onCancel={mutation.cancel} onReset={reset} />
        </form>
        <MutationResult state={mutation.state} idleText="The response will appear here.">
          {(response) => <PlaygroundResult response={response} />}
        </MutationResult>
      </div>
    </>
  );
}
