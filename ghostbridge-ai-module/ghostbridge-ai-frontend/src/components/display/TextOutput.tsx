import { MAX_RENDERED_JSON_CHARS } from '../../lib/jsonTokens';
import { CopyButton } from './CopyButton';

interface TextOutputProps {
  text: string;
  label: string;
}

/** Model output is untrusted: shown as literal text in a <pre>, never as HTML. */
export function TextOutput({ text, label }: TextOutputProps) {
  const truncated = text.length > MAX_RENDERED_JSON_CHARS;
  const shown = truncated ? text.slice(0, MAX_RENDERED_JSON_CHARS) : text;
  return (
    <div className="viewer">
      <div className="viewer__bar">
        <span className="viewer__label">{label}</span>
        <CopyButton text={shown} label="Copy response" />
      </div>
      {/* Scrollable region: must be keyboard focusable so keyboard users can scroll it. */}
      {/* eslint-disable-next-line jsx-a11y/no-noninteractive-tabindex */}
      <pre className="viewer__body viewer__body--wrap" tabIndex={0} aria-label={label}>
        {shown}
      </pre>
      {truncated ? <p className="muted">Display truncated. Copy copies the displayed portion only.</p> : null}
    </div>
  );
}
