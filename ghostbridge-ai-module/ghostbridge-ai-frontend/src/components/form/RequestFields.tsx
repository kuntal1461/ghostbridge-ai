import {
  newVariableRow,
  LIMITS,
  type FieldErrors,
  type RequestFieldsState,
} from '../../features/requestFields';
import { TextField } from './TextField';

interface RequestFieldsProps {
  value: RequestFieldsState;
  onChange: (next: RequestFieldsState) => void;
  errors: FieldErrors;
  disabled: boolean;
}

/** Template variables, untrusted content and generation options: shared by the Playground and Structured pages. */
export function RequestFields({ value, onChange, errors, disabled }: RequestFieldsProps) {
  const setRow = (id: string, patch: { name?: string; value?: string }) =>
    onChange({ ...value, variables: value.variables.map((row) => (row.id === id ? { ...row, ...patch } : row)) });

  return (
    <>
      <fieldset className="fieldset" disabled={disabled}>
        <legend>Template variables</legend>
        <p className="muted">The registered prompt template receives these values. Variable &quot;text&quot; is used by the default template.</p>
        {value.variables.map((row, index) => (
          <div className="variable-row" key={row.id}>
            <TextField label={`Variable ${index + 1} name`} value={row.name} error={errors[`${row.id}:name`]} onChange={(name) => setRow(row.id, { name })} />
            <TextField label={`Variable ${index + 1} value`} multiline rows={4} value={row.value} error={errors[`${row.id}:value`]} onChange={(next) => setRow(row.id, { value: next })} />
            <button
              type="button"
              className="button button--ghost"
              disabled={value.variables.length === 1}
              onClick={() => onChange({ ...value, variables: value.variables.filter((item) => item.id !== row.id) })}
            >
              Remove variable {index + 1}
            </button>
          </div>
        ))}
        {errors.variables ? <p className="field__error">{errors.variables}</p> : null}
        <button
          type="button"
          className="button button--secondary"
          disabled={value.variables.length >= LIMITS.maxVariables}
          onClick={() => onChange({ ...value, variables: [...value.variables, newVariableRow()] })}
        >
          Add variable
        </button>
      </fieldset>

      <TextField
        label="Untrusted content (optional)"
        multiline
        rows={4}
        value={value.untrustedContent}
        error={errors.untrustedContent}
        disabled={disabled}
        hint="Documents or text that must be treated as data, not instructions."
        onChange={(untrustedContent) => onChange({ ...value, untrustedContent })}
      />

      <div className="field-row">
        <TextField label="Max output tokens (optional)" inputMode="numeric" value={value.maxOutputTokens} error={errors.maxOutputTokens} disabled={disabled} onChange={(maxOutputTokens) => onChange({ ...value, maxOutputTokens })} />
        <TextField label="Temperature 0 to 1 (optional)" inputMode="decimal" value={value.temperature} error={errors.temperature} disabled={disabled} onChange={(temperature) => onChange({ ...value, temperature })} />
      </div>
    </>
  );
}
