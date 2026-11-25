interface FormActionsProps {
  pending: boolean;
  submitLabel: string;
  onCancel: () => void;
  onReset: () => void;
}

export function FormActions({ pending, submitLabel, onCancel, onReset }: FormActionsProps) {
  return (
    <div className="actions">
      <button type="submit" className="button button--primary" disabled={pending}>
        {pending ? 'Generating…' : submitLabel}
      </button>
      {pending ? (
        <button type="button" className="button button--secondary" onClick={onCancel}>
          Cancel
        </button>
      ) : null}
      <button type="button" className="button button--ghost" onClick={onReset}>
        Reset
      </button>
    </div>
  );
}
