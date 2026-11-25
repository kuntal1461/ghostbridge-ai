import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { formatJson, tokenizeJson, MAX_RENDERED_JSON_CHARS } from '../lib/jsonTokens';
import { JsonViewer } from './display/JsonViewer';
import { TextOutput } from './display/TextOutput';

describe('tokenizeJson', () => {
  it('classifies every token kind and reproduces the source exactly', () => {
    const source = formatJson({ a: 'x', b: 1.5e3, c: true, d: null, e: [1, 2], f: {} }).text;
    const tokens = tokenizeJson(source);
    expect(tokens.map((t) => t.text).join('')).toBe(source);
    expect(new Set(tokens.map((t) => t.kind))).toEqual(new Set(['key', 'string', 'number', 'boolean', 'null', 'punctuation', 'space']));
  });

  it('handles escaped quotes and markup inside strings', () => {
    const source = JSON.stringify({ k: 'he said "<b>hi</b>"' });
    expect(tokenizeJson(source).map((t) => t.text).join('')).toBe(source);
  });
});

describe('output components', () => {
  it('JsonViewer shows primitives and empty arrays', () => {
    render(<JsonViewer value={[]} label="empty" />);
    expect(screen.getByLabelText('empty')).toHaveTextContent('[]');
    render(<JsonViewer value={null} label="nothing" />);
    expect(screen.getByLabelText('nothing')).toHaveTextContent('null');
  });

  it('JsonViewer never creates elements from string content', () => {
    const { container } = render(<JsonViewer value={{ html: '<iframe src="https://evil.example"></iframe>' }} label="v" />);
    expect(container.querySelector('iframe')).toBeNull();
    expect(container.querySelector('a')).toBeNull();
  });

  it('TextOutput keeps markup literal', () => {
    const { container } = render(<TextOutput text={'<h1 onclick="x()">Hi</h1>'} label="t" />);
    expect(container.querySelector('h1')).toBeNull();
    expect(screen.getByLabelText('t')).toHaveTextContent('<h1 onclick="x()">Hi</h1>');
  });

  it('truncates very large JSON for display and says so', () => {
    const { truncated, text } = formatJson({ big: 'a'.repeat(MAX_RENDERED_JSON_CHARS) });
    expect(truncated).toBe(true);
    expect(text.length).toBe(MAX_RENDERED_JSON_CHARS);
    render(<JsonViewer value={{ big: 'a'.repeat(MAX_RENDERED_JSON_CHARS) }} label="big" />);
    expect(screen.getByText(/Display truncated/)).toBeInTheDocument();
  });

  it('TextOutput truncates huge text for display', () => {
    render(<TextOutput text={'a'.repeat(MAX_RENDERED_JSON_CHARS + 10)} label="huge" />);
    expect(screen.getByLabelText('huge').textContent).toHaveLength(MAX_RENDERED_JSON_CHARS);
    expect(screen.getByText(/Display truncated/)).toBeInTheDocument();
  });
});
