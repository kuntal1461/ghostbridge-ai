import { useMemo } from 'react';
import { formatJson, tokenizeJson } from '../../lib/jsonTokens';
import { CopyButton } from './CopyButton';

interface JsonViewerProps {
  value: unknown;
  label: string;
}

/**
 * Renders already-parsed JSON as highlighted plain text. Every token is a React text child: nothing is interpreted as
 * HTML or executed, and there are no links.
 */
export function JsonViewer({ value, label }: JsonViewerProps) {
  const { text, truncated } = useMemo(() => formatJson(value), [value]);
  const tokens = useMemo(() => tokenizeJson(text), [text]);
  return (
    <div className="viewer">
      <div className="viewer__bar">
        <span className="viewer__label">{label}</span>
        <CopyButton text={text} label="Copy JSON" />
      </div>
      {/* Scrollable region: must be keyboard focusable so keyboard users can scroll it. */}
      {/* eslint-disable-next-line jsx-a11y/no-noninteractive-tabindex */}
      <pre className="viewer__body" tabIndex={0} aria-label={label}>
        <code>
          {tokens.map((token, index) => (
            <span key={index} className={`json-${token.kind}`}>
              {token.text}
            </span>
          ))}
        </code>
      </pre>
      {truncated ? <p className="muted">Display truncated. Copy JSON copies the displayed portion only.</p> : null}
    </div>
  );
}
