import { useId, type ReactNode } from 'react';

interface BaseProps {
  label: string;
  value: string;
  onChange: (value: string) => void;
  error?: string;
  hint?: string;
  disabled?: boolean;
  required?: boolean;
}

interface TextFieldProps extends BaseProps {
  multiline?: boolean;
  rows?: number;
  inputMode?: 'text' | 'numeric' | 'decimal';
  placeholder?: string;
  autoComplete?: string;
}

/** Labelled input/textarea with an associated hint and error (aria-describedby, aria-invalid). */
export function TextField({ label, value, onChange, error, hint, disabled, required, multiline, rows = 5, inputMode, placeholder }: TextFieldProps) {
  const id = useId();
  const describedBy = [hint ? `${id}-hint` : '', error ? `${id}-error` : ''].filter(Boolean).join(' ') || undefined;
  const common = {
    id,
    value,
    disabled,
    required,
    placeholder,
    'aria-invalid': error ? true : undefined,
    'aria-describedby': describedBy,
    className: 'input',
    spellCheck: false,
    autoComplete: 'off',
  } as const;
  return (
    <div className="field">
      <label htmlFor={id}>
        {label}
        {required ? <span aria-hidden="true"> *</span> : null}
      </label>
      {multiline ? (
        <textarea {...common} rows={rows} onChange={(event) => onChange(event.target.value)} />
      ) : (
        <input {...common} type="text" inputMode={inputMode} onChange={(event) => onChange(event.target.value)} />
      )}
      <FieldMessages id={id} hint={hint} error={error} />
    </div>
  );
}

function FieldMessages({ id, hint, error }: { id: string; hint?: string; error?: string }): ReactNode {
  return (
    <>
      {hint ? <p id={`${id}-hint`} className="field__hint muted">{hint}</p> : null}
      {error ? <p id={`${id}-error`} className="field__error">{error}</p> : null}
    </>
  );
}
