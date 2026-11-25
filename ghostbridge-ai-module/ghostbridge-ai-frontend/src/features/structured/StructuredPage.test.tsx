import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { STRUCTURED_OK, jsonResponse, stubFetch } from '../../test/http';
import { renderPage } from '../../test/render';
import { StructuredPage } from './StructuredPage';

afterEach(() => vi.unstubAllGlobals());

async function submit() {
  const user = userEvent.setup();
  renderPage(<StructuredPage />);
  await user.type(screen.getByLabelText('Variable 1 value'), 'some text');
  await user.click(screen.getByRole('button', { name: 'Generate JSON' }));
  return user;
}

describe('StructuredPage', () => {
  it('offers only configured schema identifiers (no free-text schema)', () => {
    renderPage(<StructuredPage />);
    const select = screen.getByLabelText('Approved schema');
    expect(select.tagName).toBe('SELECT');
    expect(screen.getAllByRole('option').map((option) => option.textContent)).toEqual(['generic-summary']);
    expect(screen.queryByLabelText(/Prompt template name/)).not.toBeInTheDocument();
  });

  it('sends AiStructuredGenerateRequestVO fields only', async () => {
    const stub = stubFetch(() => jsonResponse(200, STRUCTURED_OK));
    await submit();
    await screen.findByText('Structured JSON');
    expect(stub.mock.calls[0][0]).toBe('/api/v1/ai/generate/structured');
    expect(JSON.parse(String(stub.mock.calls[0][1].body))).toEqual({ schemaName: 'generic-summary', variables: { text: 'some text' } });
  });

  it('renders formatted, highlighted JSON and copies it', async () => {
    // a richer payload than the real schema allows, only to exercise every token type of the viewer
    const structured = { summary: 'Short summary', count: 3, ok: true, none: null };
    stubFetch(() => jsonResponse(200, { ...STRUCTURED_OK, structured }));
    const user = await submit();
    const viewer = await screen.findByLabelText('Structured JSON');
    expect(viewer.querySelector('.json-key')).toHaveTextContent('"summary"');
    expect(viewer.querySelector('.json-string')).toHaveTextContent('"Short summary"');
    expect(viewer.querySelector('.json-number')).not.toBeNull();
    expect(viewer.textContent).toContain('\n  "summary"');
    const writeText = vi.spyOn(navigator.clipboard, 'writeText').mockResolvedValue();
    await user.click(screen.getByRole('button', { name: 'Copy JSON' }));
    expect(writeText).toHaveBeenCalledWith(JSON.stringify(structured, null, 2));
  });

  it('keeps hostile model strings inert', async () => {
    stubFetch(() => jsonResponse(200, { ...STRUCTURED_OK, structured: { summary: '<script>window.__x=1</script><img src=x onerror=alert(1)>' } }));
    await submit();
    const viewer = await screen.findByLabelText('Structured JSON');
    expect(viewer).toHaveTextContent('<script>window.__x=1</script>');
    expect(document.querySelector('script')).toBeNull();
    expect(document.querySelector('img')).toBeNull();
  });

  it('handles an empty JSON object', async () => {
    stubFetch(() => jsonResponse(200, { ...STRUCTURED_OK, structured: {} }));
    await submit();
    expect(await screen.findByLabelText('Structured JSON')).toHaveTextContent('{}');
  });

  it('treats a response without structured data as an unexpected response', async () => {
    stubFetch(() => jsonResponse(200, { ...STRUCTURED_OK, structured: undefined }));
    await submit();
    expect(await screen.findByRole('alert')).toHaveTextContent('Unexpected response');
  });

  it('shows the server validation error for a rejected schema (400)', async () => {
    stubFetch(() => jsonResponse(400, { code: 'VALIDATION_ERROR', detail: 'Schema is not approved' }));
    await submit();
    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Request rejected');
    expect(alert).toHaveTextContent('Schema is not approved');
  });

  it('shows the 429 state with Retry-After', async () => {
    stubFetch(() => jsonResponse(429, {}, { 'Retry-After': '1' }));
    await submit();
    expect(await screen.findByRole('alert')).toHaveTextContent('wait 1 second before');
  });

  it('blocks invalid variable names without calling the API', async () => {
    const stub = stubFetch(() => jsonResponse(200, STRUCTURED_OK));
    const user = userEvent.setup();
    renderPage(<StructuredPage />);
    await user.type(screen.getByLabelText('Variable 1 name'), '-');
    await user.click(screen.getByRole('button', { name: 'Generate JSON' }));
    expect(stub).not.toHaveBeenCalled();
  });

  it('Reset clears the result and the form', async () => {
    stubFetch(() => jsonResponse(200, STRUCTURED_OK));
    const user = await submit();
    await screen.findByText('Structured JSON');
    await user.click(screen.getByRole('button', { name: 'Reset' }));
    expect(screen.queryByText('Structured JSON')).not.toBeInTheDocument();
    expect(screen.getByLabelText('Variable 1 value')).toHaveValue('');
    expect(screen.getByLabelText('Approved schema')).toHaveValue('generic-summary');
  });
});
