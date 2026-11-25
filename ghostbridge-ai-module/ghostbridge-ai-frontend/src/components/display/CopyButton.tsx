import { useCopy } from '../../hooks/useCopy';

interface CopyButtonProps {
  text: string;
  label: string;
}

export function CopyButton({ text, label }: CopyButtonProps) {
  const { state, copy } = useCopy();
  return (
    <>
      <button type="button" className="button button--secondary" onClick={() => void copy(text)}>
        {state === 'copied' ? 'Copied' : label}
      </button>
      <span className="visually-hidden" role="status">
        {state === 'copied' ? 'Copied to clipboard' : state === 'failed' ? 'Copy failed' : ''}
      </span>
      {state === 'failed' ? <span className="muted">Copy is not available in this browser context.</span> : null}
    </>
  );
}
