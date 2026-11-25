export type JsonTokenKind = 'key' | 'string' | 'number' | 'boolean' | 'null' | 'punctuation' | 'space';

export interface JsonToken {
  kind: JsonTokenKind;
  text: string;
}

const TOKEN = /("(?:\\.|[^"\\])*")(\s*:)?|(-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)|(true|false)|(null)|([{}[\],:])|(\s+)/gy;

/**
 * Splits pretty-printed JSON into plain-text tokens. The output is rendered as React text children, never as HTML,
 * so model-generated content cannot inject markup.
 */
export function tokenizeJson(source: string): JsonToken[] {
  const tokens: JsonToken[] = [];
  TOKEN.lastIndex = 0;
  let match = TOKEN.exec(source);
  while (match !== null) {
    if (match[1] !== undefined) {
      tokens.push({ kind: match[2] === undefined ? 'string' : 'key', text: match[1] });
      if (match[2] !== undefined) tokens.push({ kind: 'punctuation', text: match[2] });
    } else if (match[3] !== undefined) tokens.push({ kind: 'number', text: match[3] });
    else if (match[4] !== undefined) tokens.push({ kind: 'boolean', text: match[4] });
    else if (match[5] !== undefined) tokens.push({ kind: 'null', text: match[5] });
    else if (match[6] !== undefined) tokens.push({ kind: 'punctuation', text: match[6] });
    else tokens.push({ kind: 'space', text: match[7] });
    match = TOKEN.exec(source);
  }
  return tokens;
}

export const MAX_RENDERED_JSON_CHARS = 200_000;

/** Pretty-prints a parsed JSON value; returns the text and whether it was truncated for display. */
export function formatJson(value: unknown): { text: string; truncated: boolean } {
  const text = JSON.stringify(value, null, 2) ?? '';
  return text.length > MAX_RENDERED_JSON_CHARS
    ? { text: text.slice(0, MAX_RENDERED_JSON_CHARS), truncated: true }
    : { text, truncated: false };
}
